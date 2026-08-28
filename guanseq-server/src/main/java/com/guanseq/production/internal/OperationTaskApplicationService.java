package com.guanseq.production.internal;

import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.product.api.RoutingReferenceProvider;
import com.guanseq.product.api.RoutingReferenceProvider.EffectiveOperation;
import com.guanseq.product.api.RoutingReferenceProvider.EffectiveRouting;
import com.guanseq.production.api.OperationTaskPage;
import com.guanseq.production.api.OperationTaskRecord;
import com.guanseq.production.api.LabelOperationTaskReferenceProvider;

@Service
public class OperationTaskApplicationService implements LabelOperationTaskReferenceProvider {
	private static final Set<String> TASK_ROLES = Set.of("PRODUCTION_OPERATOR", "PRODUCTION_MANAGER", "ADMIN");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final OperationTaskRepository taskRepository;
	private final OperationEventRepository eventRepository;
	private final ProductionOrderRepository orderRepository;
	private final ProductionOrderEventRepository orderEventRepository;
	private final RoutingReferenceProvider routingReferenceProvider;
	private final JdbcTemplate jdbcTemplate;

	OperationTaskApplicationService(CurrentWorkspaceProvider workspaceProvider, OperationTaskRepository taskRepository,
			OperationEventRepository eventRepository, ProductionOrderRepository orderRepository,
			ProductionOrderEventRepository orderEventRepository,
			RoutingReferenceProvider routingReferenceProvider, JdbcTemplate jdbcTemplate) {
		this.workspaceProvider = workspaceProvider; this.taskRepository = taskRepository; this.eventRepository = eventRepository;
		this.orderRepository = orderRepository; this.orderEventRepository = orderEventRepository;
		this.routingReferenceProvider = routingReferenceProvider; this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public OperationTaskPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access);
		var result = taskRepository.search(access.tenantOrganizationId(), normalize(query), normalizeStatus(status),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Order.asc("orderNumber"), Sort.Order.asc("sequenceNumber"))));
		return new OperationTaskPage(result.getContent().stream().map(item -> toRecord(item, events(item.getId()))).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
	}

	@Override
	@Transactional(readOnly = true)
	public java.util.Optional<OperationTaskLabelReference> findLabelTask(UUID tenantOrganizationId, UUID taskId) {
		return taskRepository.findByIdAndTenantOrganizationId(taskId, tenantOrganizationId).map(OperationTaskApplicationService::labelReference);
	}

	@Override
	@Transactional(readOnly = true)
	public List<OperationTaskLabelReference> listLabelTasks(UUID tenantOrganizationId, int limit) {
		return taskRepository.findByTenantOrganizationId(tenantOrganizationId,
				PageRequest.of(0, Math.min(100, Math.max(1, limit)), Sort.by(Sort.Direction.DESC, "updatedAt")))
				.stream().map(OperationTaskApplicationService::labelReference).toList();
	}

	private static OperationTaskLabelReference labelReference(OperationTaskEntity item) {
		return new OperationTaskLabelReference(item.getId(), item.getVersion(), item.getTaskNumber(), item.getOperationName(),
				item.getOrderNumber(), item.getWorkCenterCode(), item.getStatus());
	}

	@Transactional(readOnly = true)
	public OperationTaskRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access);
		return toRecord(requireTask(access, id), events(id));
	}

	@Transactional(readOnly = true)
	public List<OperationTaskRecord> listByOrder(String username, UUID orderId) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access);
		if (orderRepository.findByIdAndTenantOrganizationId(orderId, access.tenantOrganizationId()).isEmpty())
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "生产订单不存在或不在当前租户范围");
		return taskRepository.findByTenantOrganizationIdAndOrderIdOrderBySequenceNumberAsc(access.tenantOrganizationId(), orderId).stream()
				.map(item -> toRecord(item, events(item.getId()))).toList();
	}

	@Transactional
	public OperationTaskRecord action(String username, UUID id, OperationTaskRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username); requireRole(access);
		String requestId = currentRequestId("operation-task-action-");
		String source = normalizeSource(request.source());
		OperationTaskEntity task = requireTask(access, id);
		var completedEvent = eventRepository.findFirstByTenantOrganizationIdAndRequestIdAndActionIn(
				access.tenantOrganizationId(), requestId, List.of("START", "COMPLETE"));
		if (completedEvent.isPresent()) {
			if (!completedEvent.get().getTaskId().equals(id)) throw conflict("请求编号已用于其他工序动作，请刷新后重试");
			if (!completedEvent.get().getAction().equals(request.action())) throw conflict("请求编号已用于该工序的其他动作，请更换请求编号");
			return toRecord(requireTask(access, id), events(id));
		}
		if (task.getVersion() != request.expectedVersion()) throw conflict("工序任务已被其他用户更新，请刷新后重试");
		ProductionOrderEntity order = requireOrder(access, task.getOrderId());
		if (!Set.of("RELEASED", "IN_PROGRESS").contains(order.getStatus())) throw conflict("只有已下达或执行中订单的工序可以操作");
		String operator = resolveOperator(access, request, source);
		String from = task.getStatus();
		try {
			switch (request.action()) {
				case "START" -> {
					String shift = requireText(request.shiftName(), "开工必须填写班次");
					task.start(shift, operator, trimToNull(request.note()), requestId, access.userId());
					eventRepository.save(new OperationEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
							task.getId(), task.getOrderId(), "START", from, "IN_PROGRESS", requestId, trimToNull(request.note()),
							source, Map.of("workCenterCode", task.getWorkCenterCode(), "shiftName", shift, "operatorName", operator)));
					if ("RELEASED".equals(order.getStatus())) {
						String orderFrom = order.getStatus();
						order.transition("IN_PROGRESS", "工序开工自动转入执行", access.userId());
						orderRepository.save(order);
						orderEventRepository.save(new ProductionOrderEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
								order.getId(), "START", orderFrom, "IN_PROGRESS", requestId, "工序 " + task.getTaskNumber() + " 开工",
								Map.of("taskId", task.getId().toString(), "operationCode", task.getOperationCode())));
					}
				}
				case "COMPLETE" -> {
					if (request.completedQuantity() == null || request.completedQuantity().signum() <= 0)
						throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "完工数量必须大于零");
					task.complete(request.completedQuantity(), trimToNull(request.shiftName()), operator,
							trimToNull(request.note()), requestId, access.userId());
					eventRepository.save(new OperationEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
							task.getId(), task.getOrderId(), "COMPLETE", from, "COMPLETED", requestId, trimToNull(request.note()),
							source, Map.of("completedQuantity", request.completedQuantity(), "workCenterCode", task.getWorkCenterCode(),
									"operatorName", operator == null ? task.getOperatorName() : operator)));
				}
				default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的工序动作");
			}
			taskRepository.saveAndFlush(task);
			eventRepository.flush();
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw unprocessable(exception.getMessage());
		} catch (DataIntegrityViolationException exception) {
			throw conflict("工序动作请求编号冲突，请刷新确认结果");
		}
		return toRecord(task, events(id));
	}

	@Transactional
	public void createTasksForReleasedOrder(CurrentWorkspaceAccess access, ProductionOrderEntity order) {
		if (taskRepository.existsByTenantOrganizationIdAndOrderId(access.tenantOrganizationId(), order.getId())) return;
		LocalDate plannedStart = order.getPlannedStartDate() == null ? LocalDate.now() : order.getPlannedStartDate();
		EffectiveRouting routing = routingReferenceProvider.findEffectiveRouting(access.tenantOrganizationId(), order.getMaterialId(), plannedStart);
		if (routing.operations().isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "工艺路线至少需要一道工序");
		String requestId = currentRequestId("operation-task-create-");
		for (EffectiveOperation operation : routing.operations()) {
			OperationTaskEntity task = new OperationTaskEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
					access.workspaceId(), nextTaskNumber(), order, routing, operation, access.userId());
			taskRepository.saveAndFlush(task);
			eventRepository.save(new OperationEventEntity(access.tenantOrganizationId(), access.workspaceId(), access.userId(),
					task.getId(), task.getOrderId(), "CREATED", null, "PENDING", requestId, null,
					"SYSTEM", Map.of("sequenceNumber", task.getSequenceNumber(), "operationCode", task.getOperationCode(),
							"routingNumber", routing.routingNumber(), "routingVersion", routing.versionCode())));
		}
		eventRepository.flush();
	}

	private List<OperationEventEntity> events(UUID taskId) { return eventRepository.findByTaskIdOrderByOccurredAtDesc(taskId); }

	private OperationTaskRecord toRecord(OperationTaskEntity item, List<OperationEventEntity> events) {
		return new OperationTaskRecord(item.getId(), item.getTaskNumber(), item.getOrderId(), item.getOrderNumber(),
				item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(), item.getMaterialSpecification(),
				item.getUnit(), item.getPlannedQuantity(), item.getWorkshop(), item.getRoutingId(), item.getRoutingNumber(),
				item.getRoutingVersionCode(), item.getSourceOperationId(), item.getSequenceNumber(), item.getOperationCode(),
				item.getOperationName(), item.getWorkCenterCode(), item.getWorkCenterName(), item.getSetupMinutes(),
				item.getRunMinutesPerUnit(), item.getQueueMinutes(), item.isInspectionRequired(), item.getInstructionSummary(),
				item.getStatus(), item.getStartedAt(), item.getCompletedAt(), item.getCompletedQuantity(), item.getShiftName(),
				item.getOperatorName(), item.getNote(), item.getVersion(), item.getCreatedAt(), item.getUpdatedAt(),
				events.stream().map(event -> new OperationTaskRecord.Event(event.getId(), event.getAction(), event.getFromStatus(),
						event.getToStatus(), event.getRequestId(), event.getComment(), event.getSource(), event.getOccurredAt())).toList());
	}

	private OperationTaskEntity requireTask(CurrentWorkspaceAccess access, UUID id) {
		return taskRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工序任务不存在或不在当前租户范围"));
	}
	private ProductionOrderEntity requireOrder(CurrentWorkspaceAccess access, UUID id) {
		return orderRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "生产订单不存在或不在当前租户范围"));
	}
	private String nextTaskNumber() {
		Long sequence = jdbcTemplate.queryForObject("select nextval('production.operation_task_number_seq')", Long.class);
		return "OT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", sequence);
	}
	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		return value.trim();
	}
	private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private static String normalizeSource(String value) { return value == null || value.isBlank() ? "DESKTOP_FORM" : value.trim(); }
	private static String resolveOperator(CurrentWorkspaceAccess access, OperationTaskRecord.ActionRequest request, String source) {
		if ("MOBILE_SCAN".equals(source)) {
			String badge = requireText(request.operatorBadge(), "移动扫码必须扫描当前操作人员标签");
			if (!badge.equalsIgnoreCase(access.username())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "人员标签与当前登录账号不一致，不能代替他人执行工序动作");
			return access.username();
		}
		return "START".equals(request.action()) ? requireText(request.operatorName(), "开工必须填写操作人") : trimToNull(request.operatorName());
	}
	private static void requireRole(CurrentWorkspaceAccess access) {
		if (!TASK_ROLES.contains(access.roleCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权执行车间工序任务");
	}
	private static String currentRequestId(String prefix) {
		String requestId = MDC.get("requestId");
		return requestId == null || requestId.isBlank() ? prefix + UUID.randomUUID() : requestId;
	}
	private static String normalize(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
	private static String normalizeStatus(String value) {
		return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? "" : value.trim().toUpperCase();
	}
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
	private static ResponseStatusException unprocessable(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
}


