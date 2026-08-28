package com.guanseq.equipment.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

import com.guanseq.equipment.api.EquipmentMaintenanceGenerationRecord;
import com.guanseq.equipment.api.EquipmentMaintenancePlanPage;
import com.guanseq.equipment.api.EquipmentMaintenancePlanRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class EquipmentMaintenancePlanApplicationService {

	private static final Set<String> MAINTENANCE_ROLES = Set.of("MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> PLAN_STATUSES = Set.of("ACTIVE", "INACTIVE");
	private static final List<String> CLOSED_ORDER_STATUSES = List.of("COMPLETED", "CANCELLED");
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
	private static final int MAX_GENERATED_ITEMS = 500;

	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentAssetRepository assetRepository;
	private final EquipmentMaintenancePlanRepository planRepository;
	private final EquipmentMaintenancePlanEventRepository eventRepository;
	private final EquipmentMaintenanceGenerationRunRepository runRepository;
	private final EquipmentMaintenanceGenerationItemRepository itemRepository;
	private final EquipmentWorkOrderRepository workOrderRepository;
	private final EquipmentWorkOrderApplicationService workOrderService;

	EquipmentMaintenancePlanApplicationService(CurrentWorkspaceProvider workspaceProvider,
			EquipmentAssetRepository assetRepository, EquipmentMaintenancePlanRepository planRepository,
			EquipmentMaintenancePlanEventRepository eventRepository,
			EquipmentMaintenanceGenerationRunRepository runRepository,
			EquipmentMaintenanceGenerationItemRepository itemRepository,
			EquipmentWorkOrderRepository workOrderRepository, EquipmentWorkOrderApplicationService workOrderService) {
		this.workspaceProvider = workspaceProvider;
		this.assetRepository = assetRepository;
		this.planRepository = planRepository;
		this.eventRepository = eventRepository;
		this.runRepository = runRepository;
		this.itemRepository = itemRepository;
		this.workOrderRepository = workOrderRepository;
		this.workOrderService = workOrderService;
	}

	@Transactional(readOnly = true)
	public EquipmentMaintenancePlanPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String normalizedStatus = normalizeStatus(status);
		var result = planRepository.search(access.tenantOrganizationId(), access.workspaceId(), normalize(query),
				normalizedStatus, PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
						Sort.by(Sort.Direction.ASC, "nextDueDate").and(Sort.by("planCode"))));
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		List<EquipmentMaintenancePlanEntity> active = planRepository
				.findByTenantOrganizationIdAndWorkspaceIdAndStatusOrderByNextDueDateAsc(access.tenantOrganizationId(),
						access.workspaceId(), "ACTIVE");
		List<EquipmentMaintenancePlanEntity> allPlans = planRepository
				.findByTenantOrganizationIdAndWorkspaceIdOrderByNextDueDateAsc(access.tenantOrganizationId(),
						access.workspaceId());
		long generationDueCount = active.stream().filter(plan -> !plan.getNextDueDate().minusDays(plan.getLeadDays()).isAfter(today)).count();
		List<EquipmentMaintenancePlanRecord> items = result.getContent().stream()
				.map(plan -> toRecord(access, plan, false)).toList();
		long overdueCount = allPlans.stream().mapToLong(this::overdueCount).sum();
		List<EquipmentMaintenanceGenerationRecord> runs = runRepository
				.findByTenantOrganizationIdAndWorkspaceIdOrderByStartedAtDesc(access.tenantOrganizationId(),
						access.workspaceId(), PageRequest.ofSize(10)).stream()
				.map(run -> toRun(run, false)).toList();
		return new EquipmentMaintenancePlanPage(items, result.getTotalElements(), result.getNumber(), result.getSize(),
				result.getTotalPages(), active.size(), generationDueCount, overdueCount,
				MAINTENANCE_ROLES.contains(access.roleCode()), runs);
	}

	@Transactional(readOnly = true)
	public EquipmentMaintenancePlanRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(access, requirePlan(access, id), true);
	}

	@Transactional
	public EquipmentMaintenancePlanRecord create(String username, EquipmentMaintenancePlanRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintenanceRole(access);
		String requestId = requestId();
		var replay = planRepository.findByTenantOrganizationIdAndCreationRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return toRecord(access, replay.get(), true);
		EquipmentAssetEntity asset = requireAsset(access, request.assetId());
		if (asset.getVersion() != request.assetExpectedVersion()) throw conflict("关联设备已被其他用户修改，请刷新后重试");
		if ("INACTIVE".equals(asset.getOperatingStatus())) throw invalid("已停用设备不能建立周期维护模板");
		if (request.plannedStartTime().isAfter(request.dueTime())) throw invalid("计划开始时间不能晚于要求完成时间");
		EquipmentMaintenancePlanEntity plan = new EquipmentMaintenancePlanEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), request.planCode(), requestId, request.name(),
				request.workType(), asset, request.description(), request.priority(), request.intervalDays(), request.leadDays(),
				request.firstDueDate(), request.plannedStartTime(), request.dueTime(), request.assignee(), access.userId());
		try {
			planRepository.saveAndFlush(plan);
			audit(access, plan, "CREATED", null, "ACTIVE", request.reason(), requestId,
					Map.of("intervalDays", request.intervalDays(), "leadDays", request.leadDays(),
							"firstDueDate", request.firstDueDate().toString()));
		} catch (DataIntegrityViolationException exception) {
			throw conflict("模板编码或创建请求已存在，请刷新后确认", exception);
		}
		return toRecord(access, plan, true);
	}

	@Transactional
	public EquipmentMaintenancePlanRecord act(String username, UUID id, EquipmentMaintenancePlanRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintenanceRole(access);
		EquipmentMaintenancePlanEntity plan = requirePlan(access, id);
		String requestId = requestId();
		var replay = eventRepository.findByPlanIdAndRequestId(id, requestId);
		if (replay.isPresent()) {
			if (!replay.get().getAction().equals(eventAction(request.action()))) throw conflict("请求编号已用于其他模板动作");
			return toRecord(access, plan, true);
		}
		if (plan.getVersion() != request.expectedVersion()) throw conflict("周期维护模板已被其他用户修改，请刷新后重试");
		String expectedStatus = "ACTIVATE".equals(request.action()) ? "INACTIVE" : "ACTIVE";
		String nextStatus = "ACTIVATE".equals(request.action()) ? "ACTIVE" : "INACTIVE";
		if (!expectedStatus.equals(plan.getStatus())) throw conflict("当前模板状态不允许执行该动作");
		if ("ACTIVE".equals(nextStatus) && "INACTIVE".equals(requireAsset(access, plan.getAssetId()).getOperatingStatus())) {
			throw invalid("关联设备已停用，不能重新启用周期维护模板");
		}
		try {
			plan.changeStatus(nextStatus, access.userId());
			planRepository.saveAndFlush(plan);
			audit(access, plan, eventAction(request.action()), expectedStatus, nextStatus, request.reason(), requestId, Map.of());
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw conflict("周期维护模板已被其他用户修改，请刷新后重试", exception);
		}
		return toRecord(access, plan, true);
	}

	@Transactional
	public EquipmentMaintenanceGenerationRecord generate(String username,
			EquipmentMaintenancePlanRecord.GenerateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintenanceRole(access);
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		if (request.asOfDate().isAfter(today.plusDays(365))) throw invalid("截止日期最多允许提前一年生成");
		String requestId = requestId();
		var replay = runRepository.findByTenantOrganizationIdAndWorkspaceIdAndRequestId(access.tenantOrganizationId(),
				access.workspaceId(), requestId);
		if (replay.isPresent()) return toRun(replay.get(), true);
		EquipmentMaintenanceGenerationRunEntity run = new EquipmentMaintenanceGenerationRunEntity(
				access.tenantOrganizationId(), access.workspaceId(), requestId, request.asOfDate(), request.reason(), access.userId());
		try {
			runRepository.saveAndFlush(run);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("生成请求编号已在当前租户使用，请更换请求编号后重试", exception);
		}
		int generated = 0;
		int existing = 0;
		int skipped = 0;
		int processed = 0;
		for (EquipmentMaintenancePlanEntity plan : planRepository.lockActiveForGeneration(access.tenantOrganizationId(),
				access.workspaceId())) {
			while (!plan.getNextDueDate().minusDays(plan.getLeadDays()).isAfter(request.asOfDate())
					&& processed < MAX_GENERATED_ITEMS) {
				LocalDate dueDate = plan.getNextDueDate();
				EquipmentAssetEntity asset = requireAsset(access, plan.getAssetId());
				if ("INACTIVE".equals(asset.getOperatingStatus())) {
					itemRepository.save(new EquipmentMaintenanceGenerationItemEntity(run.getId(), plan.getId(), dueDate,
							"SKIPPED_INACTIVE_ASSET", null, "关联设备已停用，模板保持待生成以供人工处置"));
					skipped++;
					processed++;
					break;
				}
				var prior = workOrderRepository.findBySourcePlanIdAndSourceDueDate(plan.getId(), dueDate);
				if (prior.isPresent()) {
					itemRepository.save(new EquipmentMaintenanceGenerationItemEntity(run.getId(), plan.getId(), dueDate,
							"ALREADY_EXISTS", prior.get().getId(), "该模板与到期日已有工单，未重复生成"));
					existing++;
				} else {
					EquipmentWorkOrderEntity order = workOrderService.createGeneratedPlanOrder(access, plan, asset, dueDate,
							request.reason(), requestId);
					itemRepository.save(new EquipmentMaintenanceGenerationItemEntity(run.getId(), plan.getId(), dueDate,
							"GENERATED", order.getId(), "已生成受控运维工单"));
					generated++;
				}
				plan.advance(access.userId());
				planRepository.save(plan);
				processed++;
			}
			if (processed >= MAX_GENERATED_ITEMS) break;
		}
		itemRepository.flush();
		run.complete(generated, existing, skipped);
		runRepository.saveAndFlush(run);
		return toRun(run, true);
	}

	private EquipmentMaintenancePlanRecord toRecord(CurrentWorkspaceAccess access,
			EquipmentMaintenancePlanEntity plan, boolean includeEvents) {
		Instant now = Instant.now();
		long overdue = overdueCount(plan);
		List<String> overdueNumbers = workOrderRepository
				.findBySourcePlanIdAndStatusNotInAndDueAtBeforeOrderByDueAtAsc(plan.getId(), CLOSED_ORDER_STATUSES, now,
						PageRequest.ofSize(10)).stream().map(EquipmentWorkOrderEntity::getWorkOrderNumber).toList();
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		String generationStatus = "INACTIVE".equals(plan.getStatus()) ? "INACTIVE"
				: !plan.getNextDueDate().minusDays(plan.getLeadDays()).isAfter(today) ? "DUE" : "UPCOMING";
		List<EquipmentMaintenancePlanRecord.Event> events = includeEvents
				? eventRepository.findByPlanIdOrderByOccurredAtDesc(plan.getId()).stream()
						.map(event -> new EquipmentMaintenancePlanRecord.Event(event.getId(), event.getActorUserId(),
								event.getAction(), event.getFromStatus(), event.getToStatus(), event.getReason(),
								event.getRequestId(), event.getDetails(), event.getOccurredAt())).toList()
				: List.of();
		return new EquipmentMaintenancePlanRecord(plan.getId(), plan.getPlanCode(), plan.getName(), plan.getWorkType(),
				plan.getAssetId(), plan.getAssetCodeSnapshot(), plan.getAssetNameSnapshot(), plan.getAssetLocationSnapshot(),
				plan.getDescription(), plan.getPriority(), plan.getIntervalDays(), plan.getLeadDays(), plan.getFirstDueDate(),
				plan.getNextDueDate(), plan.getNextDueDate().minusDays(plan.getLeadDays()), plan.getPlannedStartTime(),
				plan.getDueTime(), plan.getAssignee(), plan.getStatus(), generationStatus, overdue, overdueNumbers,
				plan.getVersion(), plan.getCreatedAt(), plan.getUpdatedAt(),
				"ACTIVE".equals(plan.getStatus()) ? List.of("INACTIVATE") : List.of("ACTIVATE"), events);
	}

	private long overdueCount(EquipmentMaintenancePlanEntity plan) {
		return workOrderRepository.countBySourcePlanIdAndStatusNotInAndDueAtBefore(plan.getId(), CLOSED_ORDER_STATUSES,
				Instant.now());
	}

	private EquipmentMaintenanceGenerationRecord toRun(EquipmentMaintenanceGenerationRunEntity run, boolean includeItems) {
		List<EquipmentMaintenanceGenerationRecord.Item> items = includeItems
				? itemRepository.findByRunIdOrderByDueDateAsc(run.getId()).stream()
						.map(item -> new EquipmentMaintenanceGenerationRecord.Item(item.getId(), item.getPlanId(),
								item.getDueDate(), item.getOutcome(), item.getWorkOrderId(), item.getMessage())).toList()
				: List.of();
		return new EquipmentMaintenanceGenerationRecord(run.getId(), run.getRequestId(), run.getAsOfDate(),
				run.getReason(), run.getStatus(), run.getGeneratedCount(), run.getExistingCount(), run.getSkippedCount(),
				run.getActorUserId(), run.getStartedAt(), run.getCompletedAt(), items);
	}

	private void audit(CurrentWorkspaceAccess access, EquipmentMaintenancePlanEntity plan, String action,
			String fromStatus, String toStatus, String reason, String requestId, Map<String, Object> details) {
		eventRepository.saveAndFlush(new EquipmentMaintenancePlanEventEntity(access.tenantOrganizationId(),
				access.workspaceId(), access.userId(), plan.getId(), action, fromStatus, toStatus, reason, requestId, details));
	}

	private EquipmentMaintenancePlanEntity requirePlan(CurrentWorkspaceAccess access, UUID id) {
		return planRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(),
				access.workspaceId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"周期维护模板不存在或不在当前工作区范围"));
	}

	private EquipmentAssetEntity requireAsset(CurrentWorkspaceAccess access, UUID id) {
		return assetRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(),
				access.workspaceId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"设备不存在或不在当前工作区范围"));
	}

	private static void requireMaintenanceRole(CurrentWorkspaceAccess access) {
		if (!MAINTENANCE_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
				"当前角色无权维护周期设备计划");
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }

	private static String normalizeStatus(String value) {
		String normalized = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase();
		if ("ALL".equals(normalized)) return "";
		if (!PLAN_STATUSES.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"周期维护模板状态筛选无效");
		return normalized;
	}

	private static String eventAction(String action) { return "ACTIVATE".equals(action) ? "ACTIVATED" : "INACTIVATED"; }

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
