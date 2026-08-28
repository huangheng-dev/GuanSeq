package com.guanseq.equipment.internal;

import java.math.BigDecimal;
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

import com.guanseq.equipment.api.EquipmentAlertPage;
import com.guanseq.equipment.api.EquipmentAlertRecord;
import com.guanseq.equipment.api.EquipmentAlertRulePage;
import com.guanseq.equipment.api.EquipmentAlertRuleRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class EquipmentAlertApplicationService {

	private static final Set<String> MANAGER_ROLES = Set.of("MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> RULE_STATUSES = Set.of("ACTIVE", "PAUSED");
	private static final Set<String> ALERT_STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "CLOSED");
	private static final Set<String> SEVERITIES = Set.of("WARNING", "CRITICAL");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentAlertRuleRepository ruleRepository;
	private final EquipmentAlertRuleEventRepository ruleEventRepository;
	private final EquipmentAlertRepository alertRepository;
	private final EquipmentAlertEventRepository alertEventRepository;
	private final EquipmentTelemetryConnectionRepository connectionRepository;
	private final EquipmentTelemetryPointRepository pointRepository;
	private final EquipmentAssetRepository assetRepository;
	private final EquipmentWorkOrderRepository workOrderRepository;

	EquipmentAlertApplicationService(CurrentWorkspaceProvider workspaceProvider,
			EquipmentAlertRuleRepository ruleRepository, EquipmentAlertRuleEventRepository ruleEventRepository,
			EquipmentAlertRepository alertRepository, EquipmentAlertEventRepository alertEventRepository,
			EquipmentTelemetryConnectionRepository connectionRepository,
			EquipmentTelemetryPointRepository pointRepository, EquipmentAssetRepository assetRepository,
			EquipmentWorkOrderRepository workOrderRepository) {
		this.workspaceProvider = workspaceProvider;
		this.ruleRepository = ruleRepository;
		this.ruleEventRepository = ruleEventRepository;
		this.alertRepository = alertRepository;
		this.alertEventRepository = alertEventRepository;
		this.connectionRepository = connectionRepository;
		this.pointRepository = pointRepository;
		this.assetRepository = assetRepository;
		this.workOrderRepository = workOrderRepository;
	}

	@Transactional(readOnly = true)
	public EquipmentAlertRulePage listRules(String username, String status, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String normalizedStatus = normalizeFilter(status, RULE_STATUSES, "报警规则状态筛选无效");
		var result = ruleRepository.search(access.tenantOrganizationId(), access.workspaceId(), normalizedStatus,
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new EquipmentAlertRulePage(result.getContent().stream().map(rule -> toRuleRecord(access, rule, false)).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(), canManage(access));
	}

	@Transactional
	public EquipmentAlertRuleRecord createRule(String username, EquipmentAlertRuleRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		String requestId = requestId();
		var replay = ruleRepository.findByTenantOrganizationIdAndCreationRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return toRuleRecord(access, replay.get(), true);
		EquipmentTelemetryConnectionEntity connection = requireConnection(access, request.connectionId());
		if (!"ACTIVE".equals(connection.getStatus())) {
			throw invalid("只有已启用的采集连接可以建立活动报警规则");
		}
		EquipmentTelemetryPointEntity point = request.pointId() == null ? null
				: pointRepository.findByIdAndConnectionId(request.pointId(), connection.getId())
						.orElseThrow(() -> notFound("报警点位不存在或不属于所选连接"));
		validateRuleShape(request.ruleType(), request.thresholdValue(), point);
		EquipmentAlertRuleEntity rule = new EquipmentAlertRuleEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), request.ruleCode(), request.name(),
				connection.getId(), point == null ? null : point.getId(), request.ruleType(), request.thresholdValue(),
				request.severity(), request.defaultAssignee(), requestId, access.userId());
		try {
			ruleRepository.saveAndFlush(rule);
			ruleEventRepository.saveAndFlush(new EquipmentAlertRuleEventEntity(rule, access.userId(), "CREATED",
					null, "ACTIVE", request.reason(), requestId, Map.of("ruleType", rule.getRuleType())));
		} catch (DataIntegrityViolationException exception) {
			throw conflict("报警规则编码或创建请求已存在，请刷新后确认", exception);
		}
		return toRuleRecord(access, rule, true);
	}

	@Transactional
	public EquipmentAlertRuleRecord actOnRule(String username, UUID id, EquipmentAlertRuleRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		EquipmentAlertRuleEntity rule = requireRule(access, id);
		String requestId = requestId();
		var replay = ruleEventRepository.findByRuleIdAndRequestId(id, requestId);
		if (replay.isPresent()) {
			if (!eventAction(request.action()).equals(replay.get().getAction())) throw conflict("请求编号已用于其他规则动作");
			return toRuleRecord(access, rule, true);
		}
		requireVersion(rule.getVersion(), request.expectedVersion(), "报警规则已被其他用户修改，请刷新后重试");
		String from = rule.getStatus();
		String to = "ACTIVATE".equals(request.action()) ? "ACTIVE" : "PAUSED";
		if (from.equals(to)) throw conflict("报警规则已经处于目标状态");
		if ("ACTIVE".equals(to) && !"ACTIVE".equals(requireConnection(access, rule.getConnectionId()).getStatus())) {
			throw conflict("采集连接未启用，不能启用报警规则");
		}
		try {
			rule.changeStatus(to, access.userId());
			ruleRepository.saveAndFlush(rule);
			ruleEventRepository.saveAndFlush(new EquipmentAlertRuleEventEntity(rule, access.userId(),
					eventAction(request.action()), from, to, request.reason(), requestId, Map.of()));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw conflict("报警规则已被其他用户修改，请刷新后重试", exception);
		}
		return toRuleRecord(access, rule, true);
	}

	@Transactional(readOnly = true)
	public EquipmentAlertPage listAlerts(String username, String status, String severity, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String normalizedStatus = normalizeFilter(status, ALERT_STATUSES, "报警状态筛选无效");
		String normalizedSeverity = normalizeFilter(severity, SEVERITIES, "报警级别筛选无效");
		var result = alertRepository.search(access.tenantOrganizationId(), access.workspaceId(), normalizedStatus,
				normalizedSeverity, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "lastOccurredAt")));
		return new EquipmentAlertPage(result.getContent().stream().map(alert -> toAlertRecord(alert, false)).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(),
				alertRepository.countByTenantOrganizationIdAndWorkspaceIdAndConditionActiveTrue(
						access.tenantOrganizationId(), access.workspaceId()),
				alertRepository.countByTenantOrganizationIdAndWorkspaceIdAndStatusNot(
						access.tenantOrganizationId(), access.workspaceId(), "CLOSED"), canManage(access));
	}

	@Transactional(readOnly = true)
	public EquipmentAlertRecord getAlert(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toAlertRecord(requireAlert(access, id), true);
	}

	@Transactional
	public EquipmentAlertRecord actOnAlert(String username, UUID id, EquipmentAlertRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		EquipmentAlertEntity alert = requireAlert(access, id);
		String requestId = requestId();
		var replay = alertEventRepository.findByAlertIdAndRequestId(id, requestId);
		if (replay.isPresent()) {
			if (!alertEventAction(request.action()).equals(replay.get().getAction())) throw conflict("请求编号已用于其他报警动作");
			return toAlertRecord(alert, true);
		}
		requireVersion(alert.getVersion(), request.expectedVersion(), "报警已被其他用户修改，请刷新后重试");
		String from = alert.getStatus();
		Map<String, Object> details = Map.of();
		try {
			switch (request.action()) {
				case "ACKNOWLEDGE" -> {
					requireStatus(alert, "OPEN");
					alert.acknowledge(request.assignee(), access.userId());
				}
				case "START_PROCESSING" -> {
					requireStatus(alert, "ACKNOWLEDGED");
					alert.startProcessing(access.userId());
				}
				case "RESOLVE" -> {
					requireStatus(alert, "IN_PROGRESS");
					if (alert.isConditionActive()) throw conflict("报警条件仍然存在，不能标记为已解决");
					if (request.resolutionNotes() == null || request.resolutionNotes().trim().length() < 4) {
						throw invalid("解决报警必须填写至少 4 个字符的解决说明");
					}
					alert.resolve(request.resolutionNotes(), access.userId());
				}
				case "CLOSE" -> {
					requireStatus(alert, "RESOLVED");
					alert.close(access.userId());
				}
				case "LINK_REPAIR" -> {
					if (!Set.of("OPEN", "ACKNOWLEDGED", "IN_PROGRESS").contains(alert.getStatus())) {
						throw conflict("当前报警状态不能关联维修工单");
					}
					if (request.workOrderId() == null) throw invalid("关联维修必须选择维修工单");
					EquipmentWorkOrderEntity order = requireRepair(access, request.workOrderId(), alert.getAssetId());
					alert.linkRepair(order.getId(), access.userId());
					details = Map.of("workOrderId", order.getId(), "workOrderNumber", order.getWorkOrderNumber());
				}
				default -> throw conflict("当前报警状态不允许执行该动作");
			}
			alertRepository.saveAndFlush(alert);
			alertEventRepository.saveAndFlush(new EquipmentAlertEventEntity(alert, access.userId(),
					alertEventAction(request.action()), from, alert.getStatus(), request.reason(), requestId, details));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw conflict("报警已被其他用户修改，请刷新后重试", exception);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("报警动作与已有事实冲突，请刷新后重试", exception);
		}
		return toAlertRecord(alert, true);
	}

	private EquipmentAlertRuleRecord toRuleRecord(CurrentWorkspaceAccess access, EquipmentAlertRuleEntity rule,
			boolean includeEvents) {
		EquipmentTelemetryConnectionEntity connection = requireConnection(access, rule.getConnectionId());
		EquipmentAssetEntity asset = requireAsset(access, connection.getAssetId());
		EquipmentTelemetryPointEntity point = rule.getPointId() == null ? null
				: pointRepository.findByIdAndConnectionId(rule.getPointId(), connection.getId()).orElse(null);
		List<EquipmentAlertRuleRecord.Event> events = includeEvents
				? ruleEventRepository.findByRuleIdOrderByOccurredAtDesc(rule.getId()).stream()
						.map(event -> new EquipmentAlertRuleRecord.Event(event.getId(), event.getActorUserId(),
								event.getAction(), event.getFromStatus(), event.getToStatus(), event.getReason(),
								event.getRequestId(), event.getDetails(), event.getOccurredAt())).toList()
				: List.of();
		return new EquipmentAlertRuleRecord(rule.getId(), rule.getRuleCode(), rule.getName(), connection.getId(),
				connection.getConnectionCode(), connection.getName(), asset.getId(), asset.getAssetCode(), asset.getAssetName(),
				point == null ? null : point.getId(), point == null ? null : point.getPointCode(),
				point == null ? null : point.getName(), rule.getRuleType(), rule.getThresholdValue(), rule.getSeverity(),
				rule.getDefaultAssignee(), rule.getStatus(), rule.getVersion(), rule.getCreatedAt(), rule.getUpdatedAt(),
				"ACTIVE".equals(rule.getStatus()) ? List.of("PAUSE") : List.of("ACTIVATE"), events);
	}

	private EquipmentAlertRecord toAlertRecord(EquipmentAlertEntity alert, boolean includeEvents) {
		String workOrderNumber = alert.getLinkedWorkOrderId() == null ? null
				: workOrderRepository.findById(alert.getLinkedWorkOrderId()).map(EquipmentWorkOrderEntity::getWorkOrderNumber).orElse(null);
		List<EquipmentAlertRecord.Event> events = includeEvents
				? alertEventRepository.findByAlertIdOrderByOccurredAtDesc(alert.getId()).stream()
						.map(event -> new EquipmentAlertRecord.Event(event.getId(), event.getActorUserId(), event.getAction(),
								event.getFromStatus(), event.getToStatus(), event.getReason(), event.getRequestId(),
								event.getDetails(), event.getOccurredAt())).toList()
				: List.of();
		return new EquipmentAlertRecord(alert.getId(), alert.getAlertNumber(), alert.getRuleId(),
				alert.getRuleCodeSnapshot(), alert.getRuleNameSnapshot(), alert.getAssetId(), alert.getAssetCodeSnapshot(),
				alert.getAssetNameSnapshot(), alert.getConnectionId(), alert.getConnectionCodeSnapshot(), alert.getPointId(),
				alert.getPointCodeSnapshot(), alert.getPointNameSnapshot(), alert.getRuleType(), alert.getSeverity(),
				alert.getStatus(), alert.isConditionActive(), alert.getObservedValue(), alert.getObservedQuality(),
				alert.getFailureCode(), alert.getAssignee(), alert.getResolutionNotes(), alert.getLinkedWorkOrderId(),
				workOrderNumber, alert.getVersion(), alert.getFirstOccurredAt(), alert.getLastOccurredAt(),
				alert.getRecoveredAt(), alert.getAcknowledgedAt(), alert.getProcessingStartedAt(), alert.getResolvedAt(),
				alert.getClosedAt(), alert.getUpdatedAt(), availableAlertActions(alert), events);
	}

	private static List<String> availableAlertActions(EquipmentAlertEntity alert) {
		if ("OPEN".equals(alert.getStatus())) return alert.getLinkedWorkOrderId() == null
				? List.of("ACKNOWLEDGE", "LINK_REPAIR") : List.of("ACKNOWLEDGE");
		if ("ACKNOWLEDGED".equals(alert.getStatus())) return alert.getLinkedWorkOrderId() == null
				? List.of("START_PROCESSING", "LINK_REPAIR") : List.of("START_PROCESSING");
		if ("IN_PROGRESS".equals(alert.getStatus())) {
			if (alert.isConditionActive()) return alert.getLinkedWorkOrderId() == null ? List.of("LINK_REPAIR") : List.of();
			return alert.getLinkedWorkOrderId() == null ? List.of("RESOLVE", "LINK_REPAIR") : List.of("RESOLVE");
		}
		if ("RESOLVED".equals(alert.getStatus())) return List.of("CLOSE");
		return List.of();
	}

	private static void validateRuleShape(String type, BigDecimal threshold, EquipmentTelemetryPointEntity point) {
		if ("COMMUNICATION_FAILURE".equals(type)) {
			if (point != null || threshold != null) throw invalid("通讯失败规则不能配置点位或阈值");
			return;
		}
		if (point == null) throw invalid("该报警规则必须选择采集点位");
		if (Set.of("HIGH_LIMIT", "LOW_LIMIT").contains(type)) {
			if (threshold == null) throw invalid("数值阈值规则必须配置阈值");
			if ("BOOLEAN".equals(point.getValueType())) throw invalid("布尔点位不能配置数值阈值规则");
		}
	}

	private EquipmentTelemetryConnectionEntity requireConnection(CurrentWorkspaceAccess access, UUID id) {
		return connectionRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(),
				access.workspaceId()).orElseThrow(() -> notFound("采集连接不存在或不在当前工作区范围"));
	}

	private EquipmentAssetEntity requireAsset(CurrentWorkspaceAccess access, UUID id) {
		return assetRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(),
				access.workspaceId()).orElseThrow(() -> notFound("设备不存在或不在当前工作区范围"));
	}

	private EquipmentAlertRuleEntity requireRule(CurrentWorkspaceAccess access, UUID id) {
		return ruleRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(),
				access.workspaceId()).orElseThrow(() -> notFound("报警规则不存在或不在当前工作区范围"));
	}

	private EquipmentAlertEntity requireAlert(CurrentWorkspaceAccess access, UUID id) {
		return alertRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id, access.tenantOrganizationId(),
				access.workspaceId()).orElseThrow(() -> notFound("报警不存在或不在当前工作区范围"));
	}

	private EquipmentWorkOrderEntity requireRepair(CurrentWorkspaceAccess access, UUID id, UUID assetId) {
		EquipmentWorkOrderEntity order = workOrderRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id,
				access.tenantOrganizationId(), access.workspaceId()).orElseThrow(() -> notFound("维修工单不存在或不在当前工作区范围"));
		if (!"REPAIR".equals(order.getWorkType()) || !assetId.equals(order.getAssetId())
				|| Set.of("COMPLETED", "CANCELLED").contains(order.getStatus())) {
			throw invalid("只能关联同一设备的未关闭维修工单");
		}
		return order;
	}

	private static void requireStatus(EquipmentAlertEntity alert, String status) {
		if (!status.equals(alert.getStatus())) throw conflict("当前报警状态不允许执行该动作");
	}

	private static void requireVersion(long actual, long expected, String message) {
		if (actual != expected) throw conflict(message);
	}

	private static String normalizeFilter(String value, Set<String> allowed, String message) {
		if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return "";
		String normalized = value.trim().toUpperCase();
		if (!allowed.contains(normalized)) throw invalid(message);
		return normalized;
	}

	private static boolean canManage(CurrentWorkspaceAccess access) { return MANAGER_ROLES.contains(access.roleCode()); }

	private static void requireManager(CurrentWorkspaceAccess access) {
		if (!canManage(access)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权管理设备报警");
	}

	private static String eventAction(String action) { return "ACTIVATE".equals(action) ? "ACTIVATED" : "PAUSED"; }

	private static String alertEventAction(String action) {
		return switch (action) {
			case "ACKNOWLEDGE" -> "ACKNOWLEDGED";
			case "START_PROCESSING" -> "PROCESSING_STARTED";
			case "RESOLVE" -> "RESOLVED";
			case "CLOSE" -> "CLOSED";
			case "LINK_REPAIR" -> "REPAIR_LINKED";
			default -> action;
		};
	}

	private static String requestId() {
		String value = MDC.get("requestId");
		return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
	}

	private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
	private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
	private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
	private static ResponseStatusException conflict(String message, Exception cause) { return new ResponseStatusException(HttpStatus.CONFLICT, message, cause); }
}
