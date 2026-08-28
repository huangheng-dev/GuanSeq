package com.guanseq.quality.internal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.identity.api.WorkspacePermission;
import com.guanseq.quality.api.NonconformanceActionRequest;
import com.guanseq.quality.api.NonconformancePage;
import com.guanseq.quality.api.NonconformanceRecord;

@Service
public class NonconformanceApplicationService {
	private static final Set<String> READ_ROLES = Set.of("QUALITY_INSPECTOR", "QUALITY_MANAGER", "PROCUREMENT_MANAGER", "PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> REVIEW_ROLES = Set.of("QUALITY_MANAGER", "ADMIN");
	private static final Set<String> EXECUTE_ROLES = Set.of("QUALITY_INSPECTOR", "QUALITY_MANAGER", "PROCUREMENT_MANAGER", "PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> VERIFY_ROLES = Set.of("QUALITY_MANAGER", "ADMIN");
	private static final Set<String> QUEUES = Set.of("ALL", "REVIEW", "ACTION");
	private static final Set<String> STATUSES = Set.of("OPEN", "REVIEWED", "ACTION_REQUIRED", "ACTION_IN_PROGRESS", "VERIFICATION_PENDING", "CLOSED");
	private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
	private static final Set<String> SOURCES = Set.of("INCOMING_INSPECTION", "FINAL_INSPECTION");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final NonconformanceRepository repository;
	private final NonconformanceEventRepository eventRepository;
	private final JdbcTemplate jdbcTemplate;

	NonconformanceApplicationService(CurrentWorkspaceProvider workspaceProvider, NonconformanceRepository repository,
			NonconformanceEventRepository eventRepository, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider;
		this.repository = repository;
		this.eventRepository = eventRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public NonconformancePage list(String username, String query, String queue, String status, String severity,
			String sourceType, boolean overdue, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		require(access, WorkspacePermission.QUALITY_NONCONFORMANCE_READ, "当前角色无权查看不合格记录");
		String normalizedQueue = enumFilter(queue, QUEUES, "队列筛选无效", "ALL");
		String normalizedStatus = enumFilter(status, STATUSES, "状态筛选无效", "");
		String normalizedSeverity = enumFilter(severity, SEVERITIES, "严重度筛选无效", "");
		String normalizedSource = enumFilter(sourceType, SOURCES, "来源筛选无效", "");
		var result = repository.search(access.tenantOrganizationId(), access.workspaceId(), normalize(query), normalizedQueue,
				normalizedStatus, normalizedSeverity, normalizedSource, overdue,
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("caseNumber"))));
		return new NonconformancePage(result.getContent().stream().map(item -> toRecord(item, List.of())).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(), summary(access),
				WorkspacePermission.QUALITY_NONCONFORMANCE_REVIEW.allows(access.roleCode()),
				WorkspacePermission.QUALITY_CORRECTIVE_ACTION_EXECUTE.allows(access.roleCode()),
				WorkspacePermission.QUALITY_CORRECTIVE_ACTION_VERIFY.allows(access.roleCode()));
	}

	@Transactional(readOnly = true)
	public NonconformanceRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		require(access, WorkspacePermission.QUALITY_NONCONFORMANCE_READ, "当前角色无权查看不合格记录");
		NonconformanceEntity item = requireCase(access, id);
		return toRecord(item, eventRepository.findByNonconformanceIdOrderByOccurredAtAscIdAsc(id));
	}

	@Transactional
	public NonconformanceRecord act(String username, UUID id, NonconformanceActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String requestId = requestId();
		var duplicate = eventRepository.findByTenantOrganizationIdAndWorkspaceIdAndRequestId(
				access.tenantOrganizationId(), access.workspaceId(), requestId);
		if (duplicate.isPresent()) {
			if (!duplicate.get().getNonconformanceId().equals(id)) throw conflict("该请求编号已用于其他不合格记录");
			return get(username, id);
		}
		NonconformanceEntity item = requireCase(access, id);
		if (item.getVersion() != request.expectedVersion()) throw conflict("不合格记录已被其他用户更新，请刷新后重试");
		String before = item.getStatus();
		Map<String, Object> details = new LinkedHashMap<>();
		String reason;
		switch (request.action()) {
			case "REVIEW" -> {
				require(access, WorkspacePermission.QUALITY_NONCONFORMANCE_REVIEW, "当前角色无权执行不合格评审");
				requireStatus(item, "OPEN", "只有待评审记录可以提交评审");
				String severity = required(request.severity(), "必须选择严重度");
				if (!SEVERITIES.contains(severity)) throw invalid("严重度无效");
				boolean capaRequired = Boolean.TRUE.equals(request.capaRequired());
				if (("HIGH".equals(severity) || "CRITICAL".equals(severity)) && !capaRequired)
					throw invalid("高风险或严重不合格必须建立 CAPA");
				String containment = required(request.immediateContainment(), "必须填写即时遏制措施");
				String conclusion = required(request.reviewConclusion(), "必须填写评审结论");
				item.review(severity, containment, conclusion, capaRequired, access.userId());
				details.put("severity", severity); details.put("capaRequired", capaRequired);
				reason = conclusion;
			}
			case "DISPOSE" -> {
				require(access, WorkspacePermission.QUALITY_NONCONFORMANCE_REVIEW, "当前角色无权提交处置决定");
				requireStatus(item, "REVIEWED", "只有已评审记录可以提交处置决定");
				String type = required(request.dispositionType(), "必须选择处置类型");
				String decision = required(request.dispositionDecision(), "必须填写处置决定");
				String evidence = required(request.dispositionEvidence(), "必须填写处置执行证据");
				String owner = required(request.dispositionOwner(), "必须填写处置责任人");
				item.dispose(type, decision, evidence, owner, access.userId());
				details.put("dispositionType", type); details.put("owner", owner);
				reason = decision;
			}
			case "PLAN_ACTION" -> {
				require(access, WorkspacePermission.QUALITY_CORRECTIVE_ACTION_EXECUTE, "当前角色无权计划纠正措施");
				requireStatus(item, "ACTION_REQUIRED", "当前记录不在待计划纠正措施状态");
				String rootCause = required(request.rootCause(), "必须填写根因分析");
				String action = required(request.correctiveAction(), "必须填写纠正措施");
				String owner = required(request.actionOwner(), "必须填写措施责任人");
				LocalDate dueDate = request.actionDueDate();
				if (dueDate == null) throw invalid("必须填写措施到期日");
				if (dueDate.isBefore(LocalDate.now())) throw invalid("措施到期日不能早于今天");
				item.planAction(rootCause, action, owner, dueDate, access.userId());
				details.put("owner", owner); details.put("dueDate", dueDate.toString());
				reason = action;
			}
			case "COMPLETE_ACTION" -> {
				require(access, WorkspacePermission.QUALITY_CORRECTIVE_ACTION_EXECUTE, "当前角色无权提交纠正措施完成证据");
				requireStatus(item, "ACTION_IN_PROGRESS", "只有执行中的纠正措施可以提交完成证据");
				String evidence = required(request.actionCompletionEvidence(), "必须填写纠正措施完成证据");
				item.completeAction(evidence, access.userId());
				details.put("completionEvidence", evidence);
				reason = evidence;
			}
			case "VERIFY" -> {
				require(access, WorkspacePermission.QUALITY_CORRECTIVE_ACTION_VERIFY, "当前角色无权验证纠正措施有效性");
				requireStatus(item, "VERIFICATION_PENDING", "当前记录不在待验证状态");
				if (request.effective() == null) throw invalid("必须选择验证是否有效");
				String conclusion = required(request.verificationConclusion(), "必须填写有效性验证结论");
				item.verify(request.effective(), conclusion, access.userId());
				details.put("effective", request.effective());
				reason = conclusion;
			}
			case "REOPEN" -> {
				require(access, WorkspacePermission.QUALITY_NONCONFORMANCE_REVIEW, "当前角色无权重新打开不合格记录");
				requireStatus(item, "CLOSED", "只有已关闭记录可以重新打开");
				reason = required(request.reason(), "必须填写重新打开原因");
				if (reason.length() < 4) throw invalid("重新打开原因至少 4 个字符");
				item.reopen(access.userId());
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不合格记录动作无效");
		}
		repository.saveAndFlush(item);
		eventRepository.save(new NonconformanceEventEntity(access.tenantOrganizationId(), access.workspaceId(), item.getId(),
				access.userId(), access.username(), request.action(), before, item.getStatus(), reason, requestId, details));
		return toRecord(item, eventRepository.findByNonconformanceIdOrderByOccurredAtAscIdAsc(id));
	}

	void createFromInspection(CurrentWorkspaceAccess access, FinalInspectionEntity inspection, String requestId) {
		if (inspection.getRejectedQuantity() == null || inspection.getRejectedQuantity().signum() <= 0) return;
		if (repository.findByTenantOrganizationIdAndInspectionId(access.tenantOrganizationId(), inspection.getId()).isPresent()) return;
		var requestDuplicate = repository.findByTenantOrganizationIdAndWorkspaceIdAndCreateRequestId(
				access.tenantOrganizationId(), access.workspaceId(), requestId);
		if (requestDuplicate.isPresent()) {
			if (requestDuplicate.get().getInspectionId().equals(inspection.getId())) return;
			throw conflict("该请求编号已用于其他不合格记录");
		}
		NonconformanceEntity item = new NonconformanceEntity(inspection, nextNumber(), requestId, access.userId());
		repository.saveAndFlush(item);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("inspectionNumber", inspection.getInspectionNumber());
		details.put("nonconformingQuantity", inspection.getRejectedQuantity());
		details.put("sourceType", item.getSourceType());
		eventRepository.save(new NonconformanceEventEntity(access.tenantOrganizationId(), access.workspaceId(), item.getId(),
				access.userId(), access.username(), "CREATED", null, "OPEN", "检验发现不合格数量，自动建立不合格记录",
				requestId, details));
	}

	private NonconformancePage.Summary summary(CurrentWorkspaceAccess access) {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (Object[] row : repository.statusCounts(access.tenantOrganizationId(), access.workspaceId())) {
			counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
		}
		return new NonconformancePage.Summary(counts.getOrDefault("OPEN", 0L), counts.getOrDefault("REVIEWED", 0L),
				counts.getOrDefault("ACTION_REQUIRED", 0L), counts.getOrDefault("ACTION_IN_PROGRESS", 0L),
				counts.getOrDefault("VERIFICATION_PENDING", 0L), counts.getOrDefault("CLOSED", 0L),
				repository.overdueCount(access.tenantOrganizationId(), access.workspaceId()));
	}

	private NonconformanceRecord toRecord(NonconformanceEntity item, List<NonconformanceEventEntity> events) {
		boolean overdue = item.getActionDueDate() != null && item.getActionDueDate().isBefore(LocalDate.now())
				&& Set.of("ACTION_REQUIRED", "ACTION_IN_PROGRESS", "VERIFICATION_PENDING").contains(item.getStatus());
		List<NonconformanceRecord.Event> eventRecords = new ArrayList<>();
		for (NonconformanceEventEntity event : events) {
			eventRecords.add(new NonconformanceRecord.Event(event.getId(), event.getAction(), event.getFromStatus(),
					event.getToStatus(), event.getActorUserId(), event.getActorUsername(), event.getReason(),
					event.getRequestId(), event.getDetails(), event.getOccurredAt()));
		}
		return new NonconformanceRecord(item.getId(), item.getCaseNumber(), item.getSourceType(), item.getInspectionId(),
				item.getInspectionNumber(), item.getSourceDocumentId(), item.getSourceDocumentNumber(), item.getOrderId(),
				item.getOrderNumber(), item.getSupplierId(), item.getSupplierCode(), item.getSupplierName(), item.getMaterialId(),
				item.getMaterialCode(), item.getMaterialName(), item.getMaterialSpecification(), item.getUnit(),
				item.getNonconformingQuantity(), item.getDefectDescription(), item.getStatus(), item.getSeverity(),
				item.getImmediateContainment(), item.getReviewConclusion(), item.getCapaRequired(), item.getDispositionType(),
				item.getDispositionDecision(), item.getDispositionEvidence(), item.getDispositionOwner(), item.getRootCause(),
				item.getCorrectiveAction(), item.getActionOwner(), item.getActionDueDate(), overdue,
				item.getActionCompletionEvidence(), item.getVerificationEffective(), item.getVerificationConclusion(),
				item.getVersion(), item.getCreatedAt(), item.getUpdatedAt(), item.getClosedAt(), eventRecords);
	}

	private NonconformanceEntity requireCase(CurrentWorkspaceAccess access, UUID id) {
		return repository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "不合格记录不存在或不在当前工作区"));
	}

	private String nextNumber() {
		Long value = jdbcTemplate.queryForObject("select nextval('quality.nonconformance_number_seq')", Long.class);
		return "NCR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", value);
	}

	private static void require(CurrentWorkspaceAccess access, WorkspacePermission permission, String message) {
		if (!permission.allows(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
	}
	private static void requireStatus(NonconformanceEntity item, String expected, String message) {
		if (!expected.equals(item.getStatus())) throw invalid(message);
	}
	private static String required(String value, String message) {
		if (value == null || value.isBlank()) throw invalid(message);
		return value.trim();
	}
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static String enumFilter(String value, Set<String> allowed, String message, String allValue) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return allValue;
		String normalized = value.trim().toUpperCase();
		if (!allowed.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		return normalized;
	}
	private static String requestId() {
		String value = MDC.get("requestId");
		return value == null || value.isBlank() ? "quality-nc-" + UUID.randomUUID() : value;
	}
	private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
