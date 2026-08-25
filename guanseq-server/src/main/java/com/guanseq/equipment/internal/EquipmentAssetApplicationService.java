package com.guanseq.equipment.internal;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.equipment.api.EquipmentAssetPage;
import com.guanseq.equipment.api.EquipmentAssetRecord;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class EquipmentAssetApplicationService {

	private static final Set<String> MAINTENANCE_ROLES = Set.of("MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN");
	private static final Set<String> STATUSES = Set.of("IDLE", "RUNNING", "DOWN", "MAINTENANCE", "INACTIVE");
	private static final Set<String> CATEGORIES = Set.of("PRODUCTION", "QUALITY", "UTILITY", "LOGISTICS", "OTHER");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentAssetRepository assetRepository;
	private final EquipmentAssetEventRepository eventRepository;
	private final EquipmentWorkOrderApplicationService workOrderService;

	EquipmentAssetApplicationService(CurrentWorkspaceProvider workspaceProvider,
			EquipmentAssetRepository assetRepository, EquipmentAssetEventRepository eventRepository,
			EquipmentWorkOrderApplicationService workOrderService) {
		this.workspaceProvider = workspaceProvider;
		this.assetRepository = assetRepository;
		this.eventRepository = eventRepository;
		this.workOrderService = workOrderService;
	}

	@Transactional(readOnly = true)
	public EquipmentAssetPage list(String username, String query, String status, String category, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		String normalizedStatus = normalizeFilter(status, STATUSES, "设备状态筛选无效");
		String normalizedCategory = normalizeFilter(category, CATEGORIES, "设备类别筛选无效");
		var result = assetRepository.search(access.tenantOrganizationId(), access.workspaceId(), normalize(query),
				normalizedStatus, normalizedCategory,
				PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new EquipmentAssetPage(result.getContent().stream().map(asset -> toRecord(asset, false)).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(),
				MAINTENANCE_ROLES.contains(access.roleCode()));
	}

	@Transactional(readOnly = true)
	public EquipmentAssetRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(requireAsset(access, id), true);
	}

	@Transactional
	public EquipmentAssetRecord create(String username, EquipmentAssetRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintenanceRole(access);
		EquipmentAssetEntity asset = new EquipmentAssetEntity(access.tenantOrganizationId(),
				access.operatingOrganizationId(), access.workspaceId(), request.assetCode(), access.userId());
		asset.updateDetails(request.assetName(), request.category(), request.manufacturer(), request.model(),
				request.serialNumber(), request.workCenterCode(), request.workCenterName(), request.location(),
				request.responsiblePerson(), request.commissioningDate(), access.userId());
		try {
			assetRepository.saveAndFlush(asset);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "设备编码已存在，请核对后重试", exception);
		}
		audit(access, asset, "CREATED", null, "IDLE", request.reason());
		return toRecord(asset, true);
	}

	@Transactional
	public EquipmentAssetRecord update(String username, UUID id, EquipmentAssetRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintenanceRole(access);
		EquipmentAssetEntity asset = requireAsset(access, id);
		requireVersion(asset, request.expectedVersion());
		if (Set.of("RUNNING", "INACTIVE").contains(asset.getOperatingStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "运行中或已停用设备不能编辑台账");
		}
		asset.updateDetails(request.assetName(), request.category(), request.manufacturer(), request.model(),
				request.serialNumber(), request.workCenterCode(), request.workCenterName(), request.location(),
				request.responsiblePerson(), request.commissioningDate(), access.userId());
		assetRepository.saveAndFlush(asset);
		audit(access, asset, "UPDATED", asset.getOperatingStatus(), asset.getOperatingStatus(), request.reason());
		return toRecord(asset, true);
	}

	@Transactional
	public EquipmentAssetRecord act(String username, UUID id, EquipmentAssetRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintenanceRole(access);
		EquipmentAssetEntity asset = requireAsset(access, id);
		requireVersion(asset, request.expectedVersion());
		String from = asset.getOperatingStatus();
		Transition transition = transition(request.action(), from);
		asset.changeStatus(transition.toStatus(), access.userId());
		assetRepository.saveAndFlush(asset);
		if ("REPORT_BREAKDOWN".equals(request.action())) {
			String actionRequestId = requestId();
			EquipmentWorkOrderEntity repair = workOrderService.createGeneratedRepair(access, asset, "BREAKDOWN", null,
					request.reason(), actionRequestId);
			audit(access, asset, transition.eventAction(), from, transition.toStatus(), request.reason(),
					Map.of("statusSource", "MANUAL", "repairWorkOrderId", repair.getId().toString()), actionRequestId);
		} else {
			audit(access, asset, transition.eventAction(), from, transition.toStatus(), request.reason());
		}
		return toRecord(asset, true);
	}

	private static Transition transition(String action, String from) {
		return switch (action) {
			case "START" -> requireTransition(from, Set.of("IDLE"), "RUNNING", "STARTED");
			case "STOP" -> requireTransition(from, Set.of("RUNNING"), "IDLE", "STOPPED");
			case "REPORT_BREAKDOWN" -> requireTransition(from, Set.of("IDLE", "RUNNING"), "DOWN", "BREAKDOWN_REPORTED");
			case "START_MAINTENANCE", "COMPLETE_MAINTENANCE" -> throw new ResponseStatusException(HttpStatus.CONFLICT,
					"设备维修必须从保养或维修工单推进，不能从台账绕过执行与验收");
			case "INACTIVATE" -> requireTransition(from, Set.of("IDLE"), "INACTIVE", "INACTIVATED");
			default -> throw new ResponseStatusException(HttpStatus.CONFLICT, "当前设备状态不允许执行该动作");
		};
	}

	private static Transition requireTransition(String from, Set<String> allowedFrom, String to, String eventAction) {
		if (!allowedFrom.contains(from)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "当前设备状态不允许执行该动作");
		}
		return new Transition(to, eventAction);
	}

	private EquipmentAssetEntity requireAsset(CurrentWorkspaceAccess access, UUID id) {
		return assetRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id,
				access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "设备不存在或不在当前工作区范围"));
	}

	private void audit(CurrentWorkspaceAccess access, EquipmentAssetEntity asset, String action, String fromStatus,
			String toStatus, String reason) {
		audit(access, asset, action, fromStatus, toStatus, reason, Map.of("statusSource", "MANUAL"));
	}

	private void audit(CurrentWorkspaceAccess access, EquipmentAssetEntity asset, String action, String fromStatus,
			String toStatus, String reason, Map<String, Object> details) {
		audit(access, asset, action, fromStatus, toStatus, reason, details, requestId());
	}

	private void audit(CurrentWorkspaceAccess access, EquipmentAssetEntity asset, String action, String fromStatus,
			String toStatus, String reason, Map<String, Object> details, String auditRequestId) {
		eventRepository.saveAndFlush(new EquipmentAssetEventEntity(access.tenantOrganizationId(), access.workspaceId(),
				access.userId(), asset.getId(), action, fromStatus, toStatus, reason, auditRequestId,
				details));
	}

	private EquipmentAssetRecord toRecord(EquipmentAssetEntity asset, boolean includeEvents) {
		var events = includeEvents ? eventRepository.findByAssetIdOrderByOccurredAtDesc(asset.getId()).stream()
				.map(event -> new EquipmentAssetRecord.Event(event.getId(), event.getActorUserId(), event.getAction(),
						event.getFromStatus(), event.getToStatus(), event.getReason(), event.getRequestId(),
						event.getDetails(), event.getOccurredAt())).toList() : java.util.List.<EquipmentAssetRecord.Event>of();
		return new EquipmentAssetRecord(asset.getId(), asset.getAssetCode(), asset.getAssetName(), asset.getCategory(),
				asset.getManufacturer(), asset.getModel(), asset.getSerialNumber(), asset.getWorkCenterCode(),
				asset.getWorkCenterName(), asset.getLocation(), asset.getResponsiblePerson(), asset.getCommissioningDate(),
				asset.getOperatingStatus(), asset.getStatusChangedAt(), asset.getVersion(), asset.getCreatedAt(),
				asset.getUpdatedAt(), events);
	}

	private static void requireMaintenanceRole(CurrentWorkspaceAccess access) {
		if (!MAINTENANCE_ROLES.contains(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护设备台账与状态");
		}
	}

	private static void requireVersion(EquipmentAssetEntity asset, long expected) {
		if (asset.getVersion() != expected) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "设备台账或状态已被其他用户修改，请刷新后重试");
		}
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }

	private static String normalizeFilter(String value, Set<String> allowed, String message) {
		String normalized = value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase();
		if ("ALL".equals(normalized)) return "";
		if (!allowed.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		return normalized;
	}

	private static String requestId() {
		String requestId = MDC.get("requestId");
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}

	private record Transition(String toStatus, String eventAction) { }
}
