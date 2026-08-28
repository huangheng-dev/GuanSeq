package com.guanseq.equipment.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.equipment.api.EquipmentWorkOrderPage;
import com.guanseq.equipment.api.EquipmentWorkOrderRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class EquipmentWorkOrderApplicationService {

	private static final Set<String> MAINTENANCE_ROLES = Set.of("MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> TYPES = Set.of("INSPECTION", "PREVENTIVE_MAINTENANCE", "REPAIR");
	private static final Set<String> STATUSES = Set.of("PLANNED", "IN_PROGRESS", "WAITING_ACCEPTANCE", "COMPLETED", "CANCELLED");
	private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.BASIC_ISO_DATE;
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentAssetRepository assetRepository;
	private final EquipmentAssetEventRepository assetEventRepository;
	private final EquipmentWorkOrderRepository workOrderRepository;
	private final EquipmentWorkOrderEventRepository eventRepository;
	private final EquipmentMaintenanceCostQueryService costQueryService;

	EquipmentWorkOrderApplicationService(CurrentWorkspaceProvider workspaceProvider,
			EquipmentAssetRepository assetRepository, EquipmentAssetEventRepository assetEventRepository,
			EquipmentWorkOrderRepository workOrderRepository, EquipmentWorkOrderEventRepository eventRepository,
			EquipmentMaintenanceCostQueryService costQueryService) {
		this.workspaceProvider = workspaceProvider;
		this.assetRepository = assetRepository;
		this.assetEventRepository = assetEventRepository;
		this.workOrderRepository = workOrderRepository;
		this.eventRepository = eventRepository;
		this.costQueryService = costQueryService;
	}

	@Transactional(readOnly = true)
	public EquipmentWorkOrderPage list(String username, String query, String type, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String normalizedType = normalizeFilter(type, TYPES, "运维工单类型筛选无效");
		String normalizedStatus = normalizeFilter(status, STATUSES, "运维工单状态筛选无效");
		var result = workOrderRepository.search(access.tenantOrganizationId(), access.workspaceId(), normalize(query),
				normalizedType, normalizedStatus, PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
						Sort.by(Sort.Direction.ASC, "dueAt").and(Sort.by(Sort.Direction.DESC, "createdAt"))));
		return new EquipmentWorkOrderPage(result.getContent().stream().map(order -> toRecord(access, order, false)).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(),
				MAINTENANCE_ROLES.contains(access.roleCode()));
	}

	@Transactional(readOnly = true)
	public EquipmentWorkOrderRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(access, requireOrder(access, id), true);
	}

	@Transactional
	public EquipmentWorkOrderRecord create(String username, EquipmentWorkOrderRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintenanceRole(access);
		String requestId = requestId();
		var replay = workOrderRepository.findByTenantOrganizationIdAndCreationRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return toRecord(access, replay.get(), true);
		EquipmentAssetEntity asset = requireAsset(access, request.assetId());
		requireAssetVersion(asset, request.assetExpectedVersion());
		validateCreation(asset, request.workType(), request.plannedStartAt(), request.dueAt());
		if ("REPAIR".equals(request.workType()) && !workOrderRepository.findOpenRepairs(asset.getId(), PageRequest.ofSize(1)).isEmpty()) {
			throw conflict("该设备已有未关闭维修工单，请继续处理原工单");
		}
		EquipmentWorkOrderEntity order = new EquipmentWorkOrderEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextNumber(request.workType()), requestId,
				request.workType(), "MANUAL", null, asset, request.title(), request.description(), request.priority(),
				request.plannedStartAt(), request.dueAt(), request.assignee(), access.userId());
		try {
			workOrderRepository.saveAndFlush(order);
			auditWorkOrder(access, order, "CREATED", null, "PLANNED", request.reason(), null, requestId,
					Map.of("sourceType", "MANUAL"));
		} catch (DataIntegrityViolationException exception) {
			throw conflict("运维工单创建请求或活动干预与已有事实冲突，请刷新后确认", exception);
		}
		return toRecord(access, order, true);
	}

	@Transactional
	public EquipmentWorkOrderRecord act(String username, UUID id, EquipmentWorkOrderRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintenanceRole(access);
		EquipmentWorkOrderEntity order = requireOrder(access, id);
		String requestId = requestId();
		var replay = eventRepository.findByWorkOrderIdAndRequestId(id, requestId);
		if (replay.isPresent()) {
			if (!replay.get().getAction().equals(eventAction(request.action()))) throw conflict("请求编号已用于其他工单动作");
			return toRecord(access, order, true);
		}
		requireOrderVersion(order, request.expectedVersion());
		EquipmentAssetEntity asset = requireAsset(access, order.getAssetId());
		requireAssetVersion(asset, request.assetExpectedVersion());
		try {
			switch (request.action()) {
				case "START" -> start(access, order, asset, request.reason(), requestId);
				case "COMPLETE" -> complete(access, order, asset, request, requestId);
				case "SUBMIT_FOR_ACCEPTANCE" -> submitForAcceptance(access, order, asset, request, requestId);
				case "ACCEPT" -> accept(access, order, asset, request.reason(), requestId);
				case "REJECT" -> reject(access, order, asset, request.reason(), requestId);
				case "CANCEL" -> cancel(access, order, request.reason(), requestId);
				default -> throw conflict("当前工单状态不允许执行该动作");
			}
			workOrderRepository.saveAndFlush(order);
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw conflict("工单或设备已被其他用户修改，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("该设备已有执行中的保养或维修工单，请刷新后确认", exception);
		}
		return toRecord(access, order, true);
	}

	EquipmentWorkOrderEntity createGeneratedRepair(CurrentWorkspaceAccess access, EquipmentAssetEntity asset,
			String sourceType, UUID sourceWorkOrderId, String reason, String requestId) {
		List<EquipmentWorkOrderEntity> existing = workOrderRepository.findOpenRepairs(asset.getId(), PageRequest.ofSize(1));
		if (!existing.isEmpty()) return existing.get(0);
		var replay = workOrderRepository.findByTenantOrganizationIdAndCreationRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return replay.get();
		Instant now = Instant.now();
		EquipmentWorkOrderEntity repair = new EquipmentWorkOrderEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextNumber("REPAIR"), requestId, "REPAIR",
				sourceType, sourceWorkOrderId, asset, "设备故障维修：" + asset.getAssetName(), reason, "HIGH", now,
				now.plusSeconds(24 * 60 * 60), asset.getResponsiblePerson(), access.userId());
		workOrderRepository.saveAndFlush(repair);
		auditWorkOrder(access, repair, "REPAIR_GENERATED", null, "PLANNED", reason, null, requestId,
				Map.of("sourceType", sourceType));
		return repair;
	}

	EquipmentWorkOrderEntity createGeneratedPlanOrder(CurrentWorkspaceAccess access, EquipmentMaintenancePlanEntity plan,
			EquipmentAssetEntity asset, LocalDate dueDate, String reason, String requestId) {
		var existing = workOrderRepository.findBySourcePlanIdAndSourceDueDate(plan.getId(), dueDate);
		if (existing.isPresent()) return existing.get();
		Instant plannedStartAt = dueDate.atTime(plan.getPlannedStartTime()).atZone(BUSINESS_ZONE).toInstant();
		Instant dueAt = dueDate.atTime(plan.getDueTime()).atZone(BUSINESS_ZONE).toInstant();
		String creationRequestId = "PLAN-" + plan.getId() + "-" + dueDate;
		EquipmentWorkOrderEntity order = new EquipmentWorkOrderEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), nextNumber(plan.getWorkType()), creationRequestId,
				plan.getWorkType(), "MAINTENANCE_PLAN", null, plan.getId(), dueDate, asset, plan.getName(),
				plan.getDescription(), plan.getPriority(), plannedStartAt, dueAt, plan.getAssignee(), access.userId());
		workOrderRepository.saveAndFlush(order);
		auditWorkOrder(access, order, "CREATED", null, "PLANNED", reason, null, requestId,
				Map.of("sourceType", "MAINTENANCE_PLAN", "sourcePlanId", plan.getId().toString(),
						"sourcePlanCode", plan.getPlanCode(), "sourceDueDate", dueDate.toString()));
		return order;
	}

	private void start(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order, EquipmentAssetEntity asset,
			String reason, String requestId) {
		requireStatus(order, "PLANNED");
		if ("INSPECTION".equals(order.getWorkType())) {
			requireAssetStatus(asset, Set.of("IDLE", "RUNNING"), "只有闲置或运行中设备可以开始点检");
		} else {
			if ("REPAIR".equals(order.getWorkType())) requireAssetStatus(asset, Set.of("DOWN"), "只有故障设备可以开始维修");
			else requireAssetStatus(asset, Set.of("IDLE", "DOWN"), "只有闲置或故障设备可以开始保养");
			changeAssetStatus(access, asset, "MAINTENANCE", "MAINTENANCE_STARTED", reason, requestId, order.getId());
		}
		order.transition("IN_PROGRESS", null, null, access.userId());
		auditWorkOrder(access, order, "STARTED", "PLANNED", "IN_PROGRESS", reason, null, requestId, Map.of());
	}

	private void complete(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order, EquipmentAssetEntity asset,
			EquipmentWorkOrderRecord.ActionRequest request, String requestId) {
		if ("REPAIR".equals(order.getWorkType())) throw conflict("维修工单必须先提交验收，不能直接完成");
		requireStatus(order, "IN_PROGRESS");
		requireOutcomeAndNotes(request);
		EquipmentWorkOrderEntity repair = null;
		if ("INSPECTION".equals(order.getWorkType())) {
			requireAssetStatus(asset, Set.of("IDLE", "RUNNING", "DOWN"), "设备当前正在维修或已停用，不能提交点检结论");
			if ("FAIL".equals(request.outcome())) {
				if (!"DOWN".equals(asset.getOperatingStatus())) {
					changeAssetStatus(access, asset, "DOWN", "BREAKDOWN_REPORTED", request.reason(), requestId, order.getId());
				}
				repair = createGeneratedRepair(access, asset, "INSPECTION_FAILURE", order.getId(),
						request.completionNotes(), requestId);
			}
		} else {
			requireAssetStatus(asset, Set.of("MAINTENANCE"), "设备已不在保养状态，请刷新后确认");
			String nextAssetStatus = "PASS".equals(request.outcome()) ? "IDLE" : "DOWN";
			changeAssetStatus(access, asset, nextAssetStatus, "PASS".equals(request.outcome())
					? "MAINTENANCE_COMPLETED" : "BREAKDOWN_REPORTED", request.reason(), requestId, order.getId());
			if ("FAIL".equals(request.outcome())) repair = createGeneratedRepair(access, asset,
					"MAINTENANCE_FAILURE", order.getId(), request.completionNotes(), requestId);
		}
		order.transition("COMPLETED", request.outcome(), request.completionNotes(), access.userId());
		Map<String, Object> details = repair == null ? Map.of() : Map.of("repairWorkOrderId", repair.getId());
		auditWorkOrder(access, order, "EXECUTION_COMPLETED", "IN_PROGRESS", "COMPLETED", request.reason(),
				request.outcome(), requestId, details);
	}

	private void submitForAcceptance(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order,
			EquipmentAssetEntity asset, EquipmentWorkOrderRecord.ActionRequest request, String requestId) {
		if (!"REPAIR".equals(order.getWorkType())) throw conflict("只有维修工单需要提交验收");
		requireStatus(order, "IN_PROGRESS");
		requireAssetStatus(asset, Set.of("MAINTENANCE"), "设备已不在维修状态，请刷新后确认");
		requireNotes(request.completionNotes(), "提交维修验收必须填写维修结果");
		order.transition("WAITING_ACCEPTANCE", null, request.completionNotes(), access.userId());
		auditWorkOrder(access, order, "SUBMITTED_FOR_ACCEPTANCE", "IN_PROGRESS", "WAITING_ACCEPTANCE",
				request.reason(), null, requestId, Map.of());
	}

	private void accept(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order, EquipmentAssetEntity asset,
			String reason, String requestId) {
		requireTypeAndStatus(order, "REPAIR", "WAITING_ACCEPTANCE");
		requireAssetStatus(asset, Set.of("MAINTENANCE"), "设备已不在维修状态，请刷新后确认");
		changeAssetStatus(access, asset, "IDLE", "MAINTENANCE_COMPLETED", reason, requestId, order.getId());
		order.transition("COMPLETED", "PASS", null, access.userId());
		auditWorkOrder(access, order, "ACCEPTED", "WAITING_ACCEPTANCE", "COMPLETED", reason, "PASS", requestId,
				Map.of());
	}

	private void reject(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order, EquipmentAssetEntity asset,
			String reason, String requestId) {
		requireTypeAndStatus(order, "REPAIR", "WAITING_ACCEPTANCE");
		requireAssetStatus(asset, Set.of("MAINTENANCE"), "设备已不在维修状态，请刷新后确认");
		order.transition("IN_PROGRESS", null, null, access.userId());
		auditWorkOrder(access, order, "REJECTED", "WAITING_ACCEPTANCE", "IN_PROGRESS", reason, "FAIL", requestId,
				Map.of());
	}

	private void cancel(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order, String reason, String requestId) {
		requireStatus(order, "PLANNED");
		order.transition("CANCELLED", null, null, access.userId());
		auditWorkOrder(access, order, "CANCELLED", "PLANNED", "CANCELLED", reason, null, requestId, Map.of());
	}

	private void changeAssetStatus(CurrentWorkspaceAccess access, EquipmentAssetEntity asset, String toStatus,
			String action, String reason, String requestId, UUID workOrderId) {
		String fromStatus = asset.getOperatingStatus();
		asset.changeStatus(toStatus, access.userId());
		assetRepository.saveAndFlush(asset);
		assetEventRepository.saveAndFlush(new EquipmentAssetEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), asset.getId(), action, fromStatus, toStatus, reason, requestId,
				Map.of("statusSource", "WORK_ORDER", "workOrderId", workOrderId.toString())));
	}

	private void auditWorkOrder(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order, String action,
			String fromStatus, String toStatus, String reason, String outcome, String requestId,
			Map<String, Object> details) {
		eventRepository.saveAndFlush(new EquipmentWorkOrderEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), order.getId(), action, fromStatus, toStatus, reason, outcome, requestId, details));
	}

	private EquipmentWorkOrderRecord toRecord(CurrentWorkspaceAccess access, EquipmentWorkOrderEntity order,
			boolean includeEvents) {
		EquipmentAssetEntity asset = requireAsset(access, order.getAssetId());
		var events = includeEvents ? eventRepository.findByWorkOrderIdOrderByOccurredAtDesc(order.getId()).stream()
				.map(event -> new EquipmentWorkOrderRecord.Event(event.getId(), event.getActorUserId(), event.getAction(),
						event.getFromStatus(), event.getToStatus(), event.getReason(), event.getOutcome(), event.getRequestId(),
						event.getDetails(), event.getOccurredAt())).toList() : List.<EquipmentWorkOrderRecord.Event>of();
		return new EquipmentWorkOrderRecord(order.getId(), order.getWorkOrderNumber(), order.getWorkType(),
				order.getSourceType(), order.getSourceWorkOrderId(), order.getSourcePlanId(), order.getSourceDueDate(),
				order.getAssetId(), order.getAssetCodeSnapshot(),
				order.getAssetNameSnapshot(), order.getAssetLocationSnapshot(), asset.getOperatingStatus(), asset.getVersion(),
				order.getTitle(), order.getDescription(), order.getPriority(), order.getStatus(), order.getPlannedStartAt(),
				order.getDueAt(), order.getAssignee(), order.getOutcome(), order.getCompletionNotes(), order.getStartedAt(),
				order.getSubmittedAt(), order.getCompletedAt(), order.getVersion(), order.getCreatedAt(), order.getUpdatedAt(),
				includeEvents && "REPAIR".equals(order.getWorkType()) ? costQueryService.get(order) : null,
				availableActions(order), events);
	}

	private static List<String> availableActions(EquipmentWorkOrderEntity order) {
		if ("PLANNED".equals(order.getStatus())) return List.of("START", "CANCEL");
		if ("IN_PROGRESS".equals(order.getStatus())) return "REPAIR".equals(order.getWorkType())
				? List.of("SUBMIT_FOR_ACCEPTANCE") : List.of("COMPLETE");
		if ("REPAIR".equals(order.getWorkType()) && "WAITING_ACCEPTANCE".equals(order.getStatus())) {
			return List.of("ACCEPT", "REJECT");
		}
		return List.of();
	}

	private static void validateCreation(EquipmentAssetEntity asset, String workType, Instant start, Instant due) {
		if ("INACTIVE".equals(asset.getOperatingStatus())) throw invalid("已停用设备不能创建运维工单");
		if (start.isAfter(due)) throw invalid("计划开始时间不能晚于要求完成时间");
		if ("REPAIR".equals(workType) && !"DOWN".equals(asset.getOperatingStatus())) {
			throw invalid("只有故障设备可以创建维修工单；请先从设备台账报告故障");
		}
	}

	private static void requireOutcomeAndNotes(EquipmentWorkOrderRecord.ActionRequest request) {
		if (request.outcome() == null) throw invalid("完成点检或保养必须选择通过或不通过结论");
		requireNotes(request.completionNotes(), "完成点检或保养必须填写执行记录");
	}

	private static void requireNotes(String notes, String message) {
		if (notes == null || notes.trim().length() < 4) throw invalid(message);
	}

	private static void requireTypeAndStatus(EquipmentWorkOrderEntity order, String type, String status) {
		if (!type.equals(order.getWorkType()) || !status.equals(order.getStatus())) throw conflict("当前工单状态不允许执行该动作");
	}

	private static void requireStatus(EquipmentWorkOrderEntity order, String status) {
		if (!status.equals(order.getStatus())) throw conflict("当前工单状态不允许执行该动作");
	}

	private static void requireAssetStatus(EquipmentAssetEntity asset, Set<String> statuses, String message) {
		if (!statuses.contains(asset.getOperatingStatus())) throw conflict(message);
	}

	private EquipmentAssetEntity requireAsset(CurrentWorkspaceAccess access, UUID id) {
		return assetRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(),
				access.workspaceId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"设备不存在或不在当前工作区范围"));
	}

	private EquipmentWorkOrderEntity requireOrder(CurrentWorkspaceAccess access, UUID id) {
		return workOrderRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(),
				access.workspaceId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"运维工单不存在或不在当前工作区范围"));
	}

	private static void requireMaintenanceRole(CurrentWorkspaceAccess access) {
		if (!MAINTENANCE_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
				"当前角色无权维护设备运维工单");
	}

	private static void requireOrderVersion(EquipmentWorkOrderEntity order, long expected) {
		if (order.getVersion() != expected) throw conflict("运维工单已被其他用户修改，请刷新后重试");
	}

	private static void requireAssetVersion(EquipmentAssetEntity asset, long expected) {
		if (asset.getVersion() != expected) throw conflict("关联设备状态已变化，请刷新后重试");
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }

	private static String normalizeFilter(String value, Set<String> allowed, String message) {
		String normalized = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase();
		if ("ALL".equals(normalized)) return "";
		if (!allowed.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		return normalized;
	}

	private static String nextNumber(String type) {
		String prefix = switch (type) { case "INSPECTION" -> "INSP"; case "PREVENTIVE_MAINTENANCE" -> "PM"; default -> "WO"; };
		return prefix + "-" + NUMBER_DATE.format(LocalDate.now(ZoneOffset.UTC)) + "-"
				+ UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	}

	private static String eventAction(String action) {
		return switch (action) {
			case "START" -> "STARTED";
			case "COMPLETE" -> "EXECUTION_COMPLETED";
			case "SUBMIT_FOR_ACCEPTANCE" -> "SUBMITTED_FOR_ACCEPTANCE";
			case "ACCEPT" -> "ACCEPTED";
			case "REJECT" -> "REJECTED";
			case "CANCEL" -> "CANCELLED";
			default -> action;
		};
	}

	private static String requestId() {
		String requestId = MDC.get("requestId");
		String normalized = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
		if (normalized.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"X-Request-Id 不能超过 120 个字符");
		return normalized;
	}

	private static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	private static ResponseStatusException conflict(String message, Exception cause) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
	}

	private static ResponseStatusException invalid(String message) {
		return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
	}
}
