package com.guanseq.equipment.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.equipment.api.EquipmentTelemetryRetentionRecord;
import com.guanseq.equipment.api.EquipmentTelemetrySamplePage;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class EquipmentTelemetryLifecycleApplicationService {

	private static final int DEFAULT_RETENTION_DAYS = 30;
	private static final Duration DEFAULT_HISTORY_WINDOW = Duration.ofHours(24);
	private static final Duration MAX_HISTORY_WINDOW = Duration.ofDays(31);
	private static final Set<String> MANAGER_ROLES = Set.of("MAINTENANCE_MANAGER", "ADMIN");
	private static final Set<String> QUALITIES = Set.of("GOOD", "UNCERTAIN", "BAD");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentTelemetryConnectionRepository connectionRepository;
	private final EquipmentTelemetrySampleRepository sampleRepository;
	private final EquipmentTelemetryRetentionPolicyRepository policyRepository;
	private final EquipmentTelemetryRetentionEventRepository eventRepository;
	private final EquipmentTelemetryRetentionRunRepository runRepository;
	private final EquipmentTelemetryRetentionAutomationService automationService;

	EquipmentTelemetryLifecycleApplicationService(CurrentWorkspaceProvider workspaceProvider,
			EquipmentTelemetryConnectionRepository connectionRepository,
			EquipmentTelemetrySampleRepository sampleRepository,
			EquipmentTelemetryRetentionPolicyRepository policyRepository,
			EquipmentTelemetryRetentionEventRepository eventRepository,
			EquipmentTelemetryRetentionRunRepository runRepository,
			EquipmentTelemetryRetentionAutomationService automationService) {
		this.workspaceProvider = workspaceProvider;
		this.connectionRepository = connectionRepository;
		this.sampleRepository = sampleRepository;
		this.policyRepository = policyRepository;
		this.eventRepository = eventRepository;
		this.runRepository = runRepository;
		this.automationService = automationService;
	}

	@Transactional(readOnly = true)
	public EquipmentTelemetrySamplePage history(String username, UUID connectionId, String pointCode,
			String quality, Instant from, Instant to, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireConnection(access, connectionId);
		Instant windowTo = to == null ? Instant.now() : to;
		Instant windowFrom = from == null ? windowTo.minus(DEFAULT_HISTORY_WINDOW) : from;
		if (windowFrom.isAfter(windowTo)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "历史查询开始时间不能晚于结束时间");
		}
		if (Duration.between(windowFrom, windowTo).compareTo(MAX_HISTORY_WINDOW) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原始样本单次查询窗口不能超过 31 天");
		}
		String normalizedPointCode = normalizePointCode(pointCode);
		String normalizedQuality = normalizeQuality(quality);
		var result = sampleRepository.findHistory(access.tenantOrganizationId(), access.workspaceId(),
				connectionId, windowFrom, windowTo, normalizedPointCode, normalizedQuality,
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
		return new EquipmentTelemetrySamplePage(result.getContent().stream().map(sample ->
				new EquipmentTelemetrySamplePage.Sample(sample.getId(), sample.getPointId(), sample.getPointCode(),
						sample.getRawValue(), sample.getNumericValue(), sample.getBooleanValue(), sample.getQuality(),
						sample.getDeviceTime(), sample.getReceivedAt(), sample.getSequenceNumber(),
						sample.getMessageVersion(), sample.getSourceProtocol())).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(),
				connectionId, windowFrom, windowTo);
	}

	@Transactional(readOnly = true)
	public EquipmentTelemetryRetentionRecord getPolicy(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(access, policyRepository.findByTenantOrganizationIdAndWorkspaceId(
				access.tenantOrganizationId(), access.workspaceId()).orElse(null));
	}

	@Transactional
	public EquipmentTelemetryRetentionRecord updatePolicy(String username,
			EquipmentTelemetryRetentionRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		String actionRequestId = requestId();
		EquipmentTelemetryRetentionEventEntity replay = findReplay(access, actionRequestId);
		if (replay != null) {
			requireReplayAction(replay, "POLICY_UPDATED");
			return toRecord(access, requirePolicy(access));
		}
		EquipmentTelemetryRetentionPolicyEntity policy = policyRepository.findForUpdate(
				access.tenantOrganizationId(), access.workspaceId()).orElse(null);
		int fromDays = policy == null ? DEFAULT_RETENTION_DAYS : policy.getRetentionDays();
		boolean fromAutomatic = policy != null && policy.isAutomaticCleanupEnabled();
		int fromInterval = policy == null ? 24 : policy.getCleanupIntervalHours();
		boolean toAutomatic = request.automaticCleanupEnabled() == null
				? fromAutomatic : request.automaticCleanupEnabled();
		int toInterval = request.cleanupIntervalHours() == null
				? fromInterval : request.cleanupIntervalHours();
		if (toAutomatic && !automationService.isSchedulerAvailable()) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"设备样本自动清理运行器未启用，当前只能保存人工保留策略");
		}
		if (policy == null) {
			if (request.expectedVersion() != 0) throw versionConflict();
			policy = new EquipmentTelemetryRetentionPolicyEntity(access.tenantOrganizationId(), access.workspaceId(),
					request.retentionDays(), access.userId());
			policy.update(request.retentionDays(), toAutomatic, toInterval, access.userId());
		} else {
			requireVersion(policy, request.expectedVersion());
			policy.update(request.retentionDays(), toAutomatic, toInterval, access.userId());
		}
		try {
			policyRepository.saveAndFlush(policy);
			eventRepository.saveAndFlush(new EquipmentTelemetryRetentionEventEntity(policy,
					access.tenantOrganizationId(), access.workspaceId(), access.userId(), "POLICY_UPDATED",
					fromDays, request.retentionDays(), null, 0, request.reason(), actionRequestId,
					fromAutomatic, toAutomatic, fromInterval, toInterval));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "保留策略或请求已被其他操作更新，请刷新后重试", exception);
		}
		return toRecord(access, policy);
	}

	@Transactional
	public EquipmentTelemetryRetentionRecord.CleanupResult cleanup(String username,
			EquipmentTelemetryRetentionRecord.CleanupRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		String actionRequestId = requestId();
		EquipmentTelemetryRetentionEventEntity replay = findReplay(access, actionRequestId);
		if (replay != null) {
			requireReplayAction(replay, "CLEANUP_COMPLETED");
			return cleanupResult(access, requirePolicy(access), replay, true);
		}
		EquipmentTelemetryRetentionPolicyEntity policy = policyRepository.findForUpdate(
				access.tenantOrganizationId(), access.workspaceId()).orElse(null);
		if (policy == null) {
			if (request.expectedVersion() != 0) throw versionConflict();
			policy = policyRepository.saveAndFlush(new EquipmentTelemetryRetentionPolicyEntity(
					access.tenantOrganizationId(), access.workspaceId(), DEFAULT_RETENTION_DAYS, access.userId()));
		} else {
			requireVersion(policy, request.expectedVersion());
		}
		Instant cutoffAt = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);
		long deletedSampleCount = sampleRepository.deleteExpired(access.tenantOrganizationId(),
				access.workspaceId(), cutoffAt);
		EquipmentTelemetryRetentionEventEntity event = new EquipmentTelemetryRetentionEventEntity(policy,
				access.tenantOrganizationId(), access.workspaceId(), access.userId(), "CLEANUP_COMPLETED",
				policy.getRetentionDays(), policy.getRetentionDays(), cutoffAt, deletedSampleCount,
				request.reason(), actionRequestId, policy.isAutomaticCleanupEnabled(),
				policy.isAutomaticCleanupEnabled(), policy.getCleanupIntervalHours(), policy.getCleanupIntervalHours());
		try {
			eventRepository.saveAndFlush(event);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "清理请求已被其他操作执行，请刷新后重试", exception);
		}
		return cleanupResult(access, policy, event, false);
	}

	public EquipmentTelemetryRetentionRecord.AutomationActionResult runAutomationNow(String username,
			EquipmentTelemetryRetentionRecord.RunNowRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		EquipmentTelemetryRetentionAutomationService.ExecutionOutcome outcome = automationService.runNow(
				access, request.expectedVersion(), request.reason(), requestId());
		EquipmentTelemetryRetentionPolicyEntity policy = requirePolicy(access);
		return new EquipmentTelemetryRetentionRecord.AutomationActionResult(toRecord(access, policy),
				toAutomationRun(outcome.run()), outcome.replayed());
	}

	@Transactional
	public EquipmentTelemetryRetentionRecord.AutomationActionResult acknowledgeAutomationFailure(String username,
			UUID runId, EquipmentTelemetryRetentionRecord.AcknowledgeFailureRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		String actionRequestId = requestId();
		EquipmentTelemetryRetentionRunEntity run = runRepository.findForUpdate(runId,
				access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"自动清理运行记录不存在或不在当前工作区范围"));
		boolean replayed = false;
		if ("ACKNOWLEDGED".equals(run.getAttentionStatus())) {
			if (!actionRequestId.equals(run.getAcknowledgementRequestId())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "该失败责任已由其他操作确认，请刷新查看");
			}
			replayed = true;
		} else if (!"OPEN".equals(run.getAttentionStatus())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "只有失败待处理记录可以确认责任");
		} else {
			run.acknowledge(access.userId(), actionRequestId, request.note());
			try {
				runRepository.saveAndFlush(run);
			} catch (DataIntegrityViolationException exception) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "失败责任确认请求已被处理，请刷新后重试", exception);
			}
		}
		return new EquipmentTelemetryRetentionRecord.AutomationActionResult(toRecord(access, requirePolicy(access)),
				toAutomationRun(run), replayed);
	}

	private EquipmentTelemetryRetentionRecord toRecord(CurrentWorkspaceAccess access,
			EquipmentTelemetryRetentionPolicyEntity policy) {
		int retentionDays = policy == null ? DEFAULT_RETENTION_DAYS : policy.getRetentionDays();
		Instant cutoffAt = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
		long expiredSampleCount = sampleRepository.countByTenantOrganizationIdAndWorkspaceIdAndReceivedAtBefore(
				access.tenantOrganizationId(), access.workspaceId(), cutoffAt);
		List<EquipmentTelemetryRetentionRecord.Event> events = eventRepository
				.findTop20ByTenantOrganizationIdAndWorkspaceIdOrderByOccurredAtDesc(
						access.tenantOrganizationId(), access.workspaceId()).stream()
				.map(this::toEvent).toList();
		List<EquipmentTelemetryRetentionRecord.AutomationRun> runs = policy == null ? List.of() : runRepository
				.findTop20ByTenantOrganizationIdAndWorkspaceIdOrderByStartedAtDesc(
						access.tenantOrganizationId(), access.workspaceId()).stream()
				.map(this::toAutomationRun).toList();
		return new EquipmentTelemetryRetentionRecord(policy == null ? null : policy.getId(), retentionDays,
				expiredSampleCount, cutoffAt, policy == null ? 0 : policy.getVersion(), policy == null,
				canManage(access), automationService.isSchedulerAvailable(),
				policy != null && policy.isAutomaticCleanupEnabled(),
				policy == null ? 24 : policy.getCleanupIntervalHours(),
				policy == null ? null : policy.getNextCleanupAt(),
				policy == null ? null : policy.getLastAutomationStatus(),
				policy == null ? null : policy.getLastAutomationCompletedAt(),
				policy == null ? 0 : policy.getConsecutiveFailures(),
				policy == null ? null : policy.getUpdatedBy(),
				policy == null ? null : policy.getUpdatedAt(), events, runs);
	}

	private EquipmentTelemetryRetentionRecord.CleanupResult cleanupResult(CurrentWorkspaceAccess access,
			EquipmentTelemetryRetentionPolicyEntity policy, EquipmentTelemetryRetentionEventEntity event,
			boolean replayed) {
		return new EquipmentTelemetryRetentionRecord.CleanupResult(toRecord(access, policy),
				event.getDeletedSampleCount(), event.getCutoffAt(), event.getRequestId(),
				event.getOccurredAt(), replayed);
	}

	private EquipmentTelemetryRetentionRecord.Event toEvent(EquipmentTelemetryRetentionEventEntity event) {
		return new EquipmentTelemetryRetentionRecord.Event(event.getId(), event.getActorUserId(), event.getAction(),
				event.getFromRetentionDays(), event.getToRetentionDays(),
				event.getFromAutomaticCleanupEnabled(), event.getToAutomaticCleanupEnabled(),
				event.getFromCleanupIntervalHours(), event.getToCleanupIntervalHours(),
				event.getCutoffAt(),
				event.getDeletedSampleCount(), event.getReason(), event.getRequestId(), event.getOccurredAt());
	}

	private EquipmentTelemetryRetentionRecord.AutomationRun toAutomationRun(
			EquipmentTelemetryRetentionRunEntity run) {
		return new EquipmentTelemetryRetentionRecord.AutomationRun(run.getId(), run.getTriggerType(), run.getStatus(),
				run.getInitiatedBy(), run.getInstanceId(), run.getRequestId(), run.getReason(), run.getCutoffAt(),
				run.getDeletedSampleCount(), run.getRemainingExpiredCount(), run.getFailureCode(),
				run.getFailureSummary(), run.getAttentionStatus(), List.of("MAINTENANCE_MANAGER", "ADMIN"),
				run.getAcknowledgedBy(), run.getAcknowledgedAt(), run.getAcknowledgementNote(),
				run.getStartedAt(), run.getCompletedAt());
	}

	private EquipmentTelemetryConnectionEntity requireConnection(CurrentWorkspaceAccess access, UUID id) {
		return connectionRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id,
				access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"采集连接不存在或不在当前工作区范围"));
	}

	private EquipmentTelemetryRetentionPolicyEntity requirePolicy(CurrentWorkspaceAccess access) {
		return policyRepository.findByTenantOrganizationIdAndWorkspaceId(
				access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "保留策略尚未建立，请刷新后重试"));
	}

	private EquipmentTelemetryRetentionEventEntity findReplay(CurrentWorkspaceAccess access, String actionRequestId) {
		return eventRepository.findByTenantOrganizationIdAndWorkspaceIdAndRequestId(
				access.tenantOrganizationId(), access.workspaceId(), actionRequestId).orElse(null);
	}

	private static void requireReplayAction(EquipmentTelemetryRetentionEventEntity replay, String action) {
		if (!action.equals(replay.getAction())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "同一请求编号已用于其他保留操作");
		}
	}

	private static String normalizePointCode(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
		String normalized = value.trim().toUpperCase();
		if (!normalized.matches("[A-Z0-9][A-Z0-9_-]{1,59}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "点位编码筛选无效");
		}
		return normalized;
	}

	private static String normalizeQuality(String value) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
		String normalized = value.trim().toUpperCase();
		if (!QUALITIES.contains(normalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "样本质量筛选无效");
		}
		return normalized;
	}

	private static void requireManager(CurrentWorkspaceAccess access) {
		if (!canManage(access)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权管理设备样本保留策略");
		}
	}

	private static boolean canManage(CurrentWorkspaceAccess access) {
		return MANAGER_ROLES.contains(access.roleCode());
	}

	private static void requireVersion(EquipmentTelemetryRetentionPolicyEntity policy, long expectedVersion) {
		if (policy.getVersion() != expectedVersion) throw versionConflict();
	}

	private static ResponseStatusException versionConflict() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "设备样本保留策略已被其他用户修改，请刷新后重试");
	}

	private static String requestId() {
		String value = MDC.get("requestId");
		return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
	}
}
