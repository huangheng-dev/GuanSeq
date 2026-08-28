package com.guanseq.equipment.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.equipment.api.EquipmentTelemetryFieldAcceptanceRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class EquipmentTelemetryFieldAcceptanceApplicationService {

	private static final Set<String> MAINTAIN_ROLES = Set.of("MAINTENANCE_MANAGER", "ADMIN");
	private static final Set<String> APPROVE_ROLES = Set.of("PRODUCTION_MANAGER", "ADMIN");
	private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
			.withZone(ZoneOffset.UTC);

	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentTelemetryConnectionRepository connectionRepository;
	private final EquipmentTelemetryConnectionEventRepository connectionEventRepository;
	private final EquipmentTelemetryFieldAcceptanceRepository acceptanceRepository;
	private final EquipmentTelemetryFieldAcceptanceEventRepository eventRepository;

	EquipmentTelemetryFieldAcceptanceApplicationService(CurrentWorkspaceProvider workspaceProvider,
			EquipmentTelemetryConnectionRepository connectionRepository,
			EquipmentTelemetryConnectionEventRepository connectionEventRepository,
			EquipmentTelemetryFieldAcceptanceRepository acceptanceRepository,
			EquipmentTelemetryFieldAcceptanceEventRepository eventRepository) {
		this.workspaceProvider = workspaceProvider;
		this.connectionRepository = connectionRepository;
		this.connectionEventRepository = connectionEventRepository;
		this.acceptanceRepository = acceptanceRepository;
		this.eventRepository = eventRepository;
	}

	@Transactional(readOnly = true)
	public EquipmentTelemetryFieldAcceptanceRecord.Context get(String username, UUID connectionId) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return context(access, requireConnection(access, connectionId));
	}

	@Transactional
	public EquipmentTelemetryFieldAcceptanceRecord.Context save(String username, UUID connectionId,
			EquipmentTelemetryFieldAcceptanceRecord.SaveRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintain(access);
		EquipmentTelemetryConnectionEntity connection = requireConnection(access, connectionId);
		requireFieldEligible(connection);
		validateDraft(request.testWindowStart(), request.testWindowEnd());
		String requestId = requestId();
		var existing = acceptanceRepository.findByConnectionId(connectionId);
		if (existing.isEmpty()) {
			var creationReplay = acceptanceRepository
					.findByTenantOrganizationIdAndWorkspaceIdAndCreationRequestId(
							access.tenantOrganizationId(), access.workspaceId(), requestId);
			if (creationReplay.isPresent()) return context(access, connection);
			if (request.expectedVersion() != null && request.expectedVersion() != 0L)
				throw conflict("新建现场验收单的预期版本必须为空或为 0");
			EquipmentTelemetryFieldAcceptanceEntity acceptance = new EquipmentTelemetryFieldAcceptanceEntity(
					connection, nextNumber(), requestId, access.userId());
			acceptance.update(values(request), access.userId());
			try {
				acceptanceRepository.saveAndFlush(acceptance);
				eventRepository.saveAndFlush(new EquipmentTelemetryFieldAcceptanceEventEntity(acceptance,
						access.userId(), "CREATED", null, "DRAFT", request.reason(), requestId,
						checkDetails(acceptance)));
			} catch (DataIntegrityViolationException exception) {
				throw conflict("现场验收单或创建请求已存在，请刷新后确认", exception);
			}
			return context(access, connection);
		}

		EquipmentTelemetryFieldAcceptanceEntity acceptance = existing.get();
		var replay = eventRepository.findByAcceptanceIdAndRequestId(acceptance.getId(), requestId);
		if (replay.isPresent()) {
			if (!Set.of("CREATED", "UPDATED").contains(replay.get().getAction()))
				throw conflict("请求编号已用于其他现场验收动作");
			return context(access, connection);
		}
		requireEditable(acceptance);
		if (request.expectedVersion() == null) throw invalid("修改现场验收单必须提供预期版本");
		requireVersion(acceptance.getVersion(), request.expectedVersion());
		String from = acceptance.getStatus();
		try {
			acceptance.update(values(request), access.userId());
			acceptanceRepository.saveAndFlush(acceptance);
			eventRepository.saveAndFlush(new EquipmentTelemetryFieldAcceptanceEventEntity(acceptance,
					access.userId(), "UPDATED", from, acceptance.getStatus(), request.reason(), requestId,
					checkDetails(acceptance)));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw conflict("现场验收单已被其他用户修改，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("现场验收修改与已有事实冲突，请刷新后重试", exception);
		}
		return context(access, connection);
	}

	@Transactional
	public EquipmentTelemetryFieldAcceptanceRecord.Context act(String username, UUID connectionId,
			EquipmentTelemetryFieldAcceptanceRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		EquipmentTelemetryConnectionEntity connection = requireConnection(access, connectionId);
		requireFieldEligible(connection);
		EquipmentTelemetryFieldAcceptanceEntity acceptance = acceptanceRepository.findByConnectionId(connectionId)
				.orElseThrow(() -> notFound("现场验收单尚未建立"));
		String requestId = requestId();
		String eventAction = switch (request.action()) {
			case "SUBMIT" -> "SUBMITTED";
			case "APPROVE" -> "APPROVED";
			case "REJECT" -> "REJECTED";
			default -> request.action();
		};
		var replay = eventRepository.findByAcceptanceIdAndRequestId(acceptance.getId(), requestId);
		if (replay.isPresent()) {
			if (!eventAction.equals(replay.get().getAction())) throw conflict("请求编号已用于其他现场验收动作");
			return context(access, connection);
		}
		requireVersion(acceptance.getVersion(), request.expectedVersion());
		String from = acceptance.getStatus();
		try {
			switch (request.action()) {
				case "SUBMIT" -> {
					requireMaintain(access);
					requireEditable(acceptance);
					requireSuccessfulFieldPrecheck(connection);
					validateSubmission(acceptance);
					acceptance.submit(access.userId());
				}
				case "APPROVE" -> {
					requireApprove(access);
					requireStatus(acceptance, "SUBMITTED");
					requireSuccessfulFieldPrecheck(connection);
					validateSubmission(acceptance);
					acceptance.approve(access.userId());
				}
				case "REJECT" -> {
					requireApprove(access);
					requireStatus(acceptance, "SUBMITTED");
					acceptance.reject(request.reason(), access.userId());
				}
				default -> throw invalid("不支持的现场验收动作");
			}
			acceptanceRepository.saveAndFlush(acceptance);
			eventRepository.saveAndFlush(new EquipmentTelemetryFieldAcceptanceEventEntity(acceptance,
					access.userId(), eventAction, from, acceptance.getStatus(), request.reason(), requestId,
					checkDetails(acceptance)));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw conflict("现场验收单已被其他用户修改，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("现场验收动作与已有事实冲突，请刷新后重试", exception);
		}
		return context(access, connection);
	}

	private EquipmentTelemetryFieldAcceptanceRecord.Context context(CurrentWorkspaceAccess access,
			EquipmentTelemetryConnectionEntity connection) {
		boolean eligible = fieldEligible(connection);
		boolean precheckPassed = eligible && latestTechnicalPrecheckPassed(connection);
		EquipmentTelemetryFieldAcceptanceRecord acceptance = acceptanceRepository.findByConnectionId(connection.getId())
				.map(item -> toRecord(access, item)).orElse(null);
		return new EquipmentTelemetryFieldAcceptanceRecord.Context(connection.getId(), connection.getConnectionCode(),
				connection.getName(), connection.getProtocol(), connection.getEndpointType(), eligible, precheckPassed,
				acceptance != null && "APPROVED".equals(acceptance.status()), canMaintain(access), canApprove(access),
				acceptance);
	}

	private EquipmentTelemetryFieldAcceptanceRecord toRecord(CurrentWorkspaceAccess access,
			EquipmentTelemetryFieldAcceptanceEntity acceptance) {
		List<EquipmentTelemetryFieldAcceptanceRecord.Event> events = eventRepository
				.findByAcceptanceIdOrderByOccurredAtDesc(acceptance.getId()).stream()
				.map(event -> new EquipmentTelemetryFieldAcceptanceRecord.Event(event.getId(), event.getActorUserId(),
						event.getAction(), event.getFromStatus(), event.getToStatus(), event.getReason(),
						event.getRequestId(), event.getDetails(), event.getOccurredAt())).toList();
		return new EquipmentTelemetryFieldAcceptanceRecord(acceptance.getId(), acceptance.getAcceptanceNumber(),
				acceptance.getConnectionId(), acceptance.getStatus(), acceptance.isNetworkApproved(),
				acceptance.isSecurityValidated(), acceptance.isReadOnlyConfirmed(),
				acceptance.isDisconnectRecoveryVerified(), acceptance.isCapacityVerified(),
				acceptance.isPointMappingApproved(), acceptance.getResponsibleOwner(),
				acceptance.getTestWindowStart(), acceptance.getTestWindowEnd(), acceptance.getEvidenceReference(),
				acceptance.getNotes(), acceptance.getRejectionReason(), acceptance.getVersion(),
				acceptance.getCreatedBy(), acceptance.getCreatedAt(), acceptance.getSubmittedBy(),
				acceptance.getSubmittedAt(), acceptance.getApprovedBy(), acceptance.getApprovedAt(),
				acceptance.getRejectedBy(), acceptance.getRejectedAt(), acceptance.getUpdatedAt(),
				availableActions(access, acceptance), events);
	}

	private boolean latestTechnicalPrecheckPassed(EquipmentTelemetryConnectionEntity connection) {
		return connectionEventRepository.findByConnectionIdOrderByOccurredAtDesc(connection.getId()).stream()
				.filter(event -> event.getDetails() != null && event.getDetails().containsKey("verificationVersion"))
				.findFirst().map(event -> "FIELD_CANDIDATE_PRECHECK".equals(event.getDetails().get("evidenceLevel"))
						&& Boolean.TRUE.equals(event.getDetails().get("technicalPassed"))).orElse(false);
	}

	private void requireSuccessfulFieldPrecheck(EquipmentTelemetryConnectionEntity connection) {
		if (!latestTechnicalPrecheckPassed(connection))
			throw invalid("最新一次现场候选端点技术预检必须成功，才能提交或批准现场验收");
	}

	private static void validateDraft(Instant start, Instant end) {
		if ((start == null) != (end == null)) throw invalid("测试窗口开始和结束时间必须同时填写或同时留空");
		if (start != null && !end.isAfter(start)) throw invalid("测试窗口结束时间必须晚于开始时间");
	}

	private static void validateSubmission(EquipmentTelemetryFieldAcceptanceEntity acceptance) {
		if (!acceptance.isNetworkApproved() || !acceptance.isSecurityValidated()
				|| !acceptance.isReadOnlyConfirmed() || !acceptance.isDisconnectRecoveryVerified()
				|| !acceptance.isCapacityVerified() || !acceptance.isPointMappingApproved())
			throw invalid("六项现场核验必须全部取得真实证据后才能提交");
		if (blank(acceptance.getResponsibleOwner())) throw invalid("提交前必须填写现场责任人");
		if (acceptance.getTestWindowStart() == null || acceptance.getTestWindowEnd() == null
				|| !acceptance.getTestWindowEnd().isAfter(acceptance.getTestWindowStart()))
			throw invalid("提交前必须填写有效的现场测试窗口");
		if (blank(acceptance.getEvidenceReference())) throw invalid("提交前必须填写可追溯的现场证据引用");
	}

	private static EquipmentTelemetryFieldAcceptanceRecordValues values(
			EquipmentTelemetryFieldAcceptanceRecord.SaveRequest request) {
		return new EquipmentTelemetryFieldAcceptanceRecordValues(request.networkApproved(), request.securityValidated(),
				request.readOnlyConfirmed(), request.disconnectRecoveryVerified(), request.capacityVerified(),
				request.pointMappingApproved(), request.responsibleOwner(), request.testWindowStart(),
				request.testWindowEnd(), request.evidenceReference(), request.notes());
	}

	private static Map<String, Object> checkDetails(EquipmentTelemetryFieldAcceptanceEntity acceptance) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("networkApproved", acceptance.isNetworkApproved());
		details.put("securityValidated", acceptance.isSecurityValidated());
		details.put("readOnlyConfirmed", acceptance.isReadOnlyConfirmed());
		details.put("disconnectRecoveryVerified", acceptance.isDisconnectRecoveryVerified());
		details.put("capacityVerified", acceptance.isCapacityVerified());
		details.put("pointMappingApproved", acceptance.isPointMappingApproved());
		details.put("completedCheckCount", completedCheckCount(acceptance));
		return Map.copyOf(details);
	}

	private static int completedCheckCount(EquipmentTelemetryFieldAcceptanceEntity acceptance) {
		return (acceptance.isNetworkApproved() ? 1 : 0) + (acceptance.isSecurityValidated() ? 1 : 0)
				+ (acceptance.isReadOnlyConfirmed() ? 1 : 0)
				+ (acceptance.isDisconnectRecoveryVerified() ? 1 : 0)
				+ (acceptance.isCapacityVerified() ? 1 : 0)
				+ (acceptance.isPointMappingApproved() ? 1 : 0);
	}

	private EquipmentTelemetryConnectionEntity requireConnection(CurrentWorkspaceAccess access, UUID id) {
		return connectionRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id,
				access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> notFound("采集连接不存在或不在当前工作区范围"));
	}

	private static boolean fieldEligible(EquipmentTelemetryConnectionEntity connection) {
		return !"SIMULATOR".equals(connection.getEndpointType());
	}
	private static void requireFieldEligible(EquipmentTelemetryConnectionEntity connection) {
		if (!fieldEligible(connection)) throw invalid("仿真端点只能形成技术证据，不能建立现场验收单");
	}
	private static boolean canMaintain(CurrentWorkspaceAccess access) { return MAINTAIN_ROLES.contains(access.roleCode()); }
	private static boolean canApprove(CurrentWorkspaceAccess access) { return APPROVE_ROLES.contains(access.roleCode()); }
	private static void requireMaintain(CurrentWorkspaceAccess access) {
		if (!canMaintain(access)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护现场验收单");
	}
	private static void requireApprove(CurrentWorkspaceAccess access) {
		if (!canApprove(access)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权审核现场验收单");
	}
	private static void requireEditable(EquipmentTelemetryFieldAcceptanceEntity acceptance) {
		if (!Set.of("DRAFT", "REJECTED").contains(acceptance.getStatus()))
			throw conflict("只有草稿或已驳回的现场验收单可以修改或提交");
	}
	private static void requireStatus(EquipmentTelemetryFieldAcceptanceEntity acceptance, String expected) {
		if (!expected.equals(acceptance.getStatus())) throw conflict("当前现场验收状态不允许执行该动作");
	}
	private static void requireVersion(long actual, long expected) {
		if (actual != expected) throw conflict("现场验收单已被其他用户修改，请刷新后重试");
	}
	private static List<String> availableActions(CurrentWorkspaceAccess access,
			EquipmentTelemetryFieldAcceptanceEntity acceptance) {
		if (Set.of("DRAFT", "REJECTED").contains(acceptance.getStatus()) && canMaintain(access))
			return List.of("UPDATE", "SUBMIT");
		if ("SUBMITTED".equals(acceptance.getStatus()) && canApprove(access)) return List.of("APPROVE", "REJECT");
		return List.of();
	}
	private static boolean blank(String value) { return value == null || value.isBlank(); }
	private static String nextNumber() {
		return "TFA-" + NUMBER_TIME.format(Instant.now()) + "-"
				+ UUID.randomUUID().toString().substring(0, 6).toUpperCase();
	}
	private static String requestId() {
		String value = MDC.get("requestId");
		return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
	}
	private static ResponseStatusException notFound(String message) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
	}
	private static ResponseStatusException invalid(String message) {
		return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
	}
	private static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}
	private static ResponseStatusException conflict(String message, Exception cause) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
	}
}
