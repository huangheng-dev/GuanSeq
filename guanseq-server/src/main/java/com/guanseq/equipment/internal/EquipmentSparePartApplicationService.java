package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

import com.guanseq.equipment.api.EquipmentSparePartPage;
import com.guanseq.equipment.api.EquipmentSparePartRecord;
import com.guanseq.equipment.api.EquipmentSparePartReferenceData;
import com.guanseq.finance.api.StandardCostProvider;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.masterdata.api.MasterDataReferenceProvider;
import com.guanseq.warehouse.api.StockPositionProvider;
import com.guanseq.warehouse.api.WarehouseReferenceProvider;

@Service
public class EquipmentSparePartApplicationService {
	private static final Set<String> MAINTENANCE_ROLES = Set.of("MAINTENANCE_MANAGER", "PRODUCTION_MANAGER", "ADMIN");

	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentSparePartRepository repository;
	private final MasterDataReferenceProvider masterData;
	private final WarehouseReferenceProvider warehouseReferences;
	private final StockPositionProvider stockPositions;
	private final StandardCostProvider standardCosts;

	EquipmentSparePartApplicationService(CurrentWorkspaceProvider workspaceProvider, EquipmentSparePartRepository repository,
			MasterDataReferenceProvider masterData, WarehouseReferenceProvider warehouseReferences,
			StockPositionProvider stockPositions, StandardCostProvider standardCosts) {
		this.workspaceProvider = workspaceProvider; this.repository = repository; this.masterData = masterData;
		this.warehouseReferences = warehouseReferences; this.stockPositions = stockPositions; this.standardCosts = standardCosts;
	}

	@Transactional(readOnly = true)
	public EquipmentSparePartPage list(String username, String query, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = repository.search(access.tenantOrganizationId(), access.workspaceId(), normalize(query),
				PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size))));
		return new EquipmentSparePartPage(result.getContent().stream().map(item -> toRecord(access, item)).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(), canMaintain(access));
	}

	@Transactional(readOnly = true)
	public EquipmentSparePartReferenceData references(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return new EquipmentSparePartReferenceData(masterData.listActiveMaterials(access.tenantOrganizationId()).stream()
				.map(item -> new EquipmentSparePartReferenceData.MaterialOption(item.id(), item.code(), item.name(),
						item.specification(), item.baseUnit())).toList(),
				warehouseReferences.listActiveWarehouses(access.tenantOrganizationId()).stream()
						.map(item -> new EquipmentSparePartReferenceData.WarehouseOption(item.id(), item.code(), item.name())).toList(),
				warehouseReferences.listActiveLocations(access.tenantOrganizationId()).stream()
						.map(item -> new EquipmentSparePartReferenceData.LocationOption(item.id(), item.warehouseId(), item.code(),
								item.name(), item.locationType())).toList());
	}

	@Transactional
	public EquipmentSparePartRecord create(String username, EquipmentSparePartRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireMaintain(access);
		String requestId = requestId();
		var replay = repository.findByTenantOrganizationIdAndCreationRequestId(access.tenantOrganizationId(), requestId);
		if (replay.isPresent()) return toRecord(access, replay.get());
		var material = masterData.requireActiveMaterial(access.tenantOrganizationId(), request.materialId());
		var warehouse = warehouseReferences.listActiveWarehouses(access.tenantOrganizationId()).stream()
				.filter(item -> item.id().equals(request.preferredWarehouseId())).findFirst()
				.orElseThrow(() -> invalid("默认仓库不存在、已停用或不在当前租户范围"));
		var entity = new EquipmentSparePartEntity(access.tenantOrganizationId(), access.operatingOrganizationId(),
				access.workspaceId(), requestId, material.id(), material.code(), material.name(), material.specification(),
				material.baseUnit(), warehouse.id(), warehouse.code(), warehouse.name(), request.reorderPoint(), access.userId());
		try { repository.saveAndFlush(entity); }
		catch (DataIntegrityViolationException exception) { throw conflict("该物料已建立备件台账，或请求编号已被使用", exception); }
		return toRecord(access, entity);
	}

	private EquipmentSparePartRecord toRecord(CurrentWorkspaceAccess access, EquipmentSparePartEntity item) {
		BigDecimal available = stockPositions.getWarehousePositions(access.tenantOrganizationId(),
				item.getPreferredWarehouseId(), List.of(item.getMaterialId())).stream().findFirst()
				.map(StockPositionProvider.WarehouseStockPosition::availableQuantity).orElse(BigDecimal.ZERO);
		var cost = standardCosts.findEffectiveCost(access.tenantOrganizationId(), item.getMaterialId(),
				LocalDate.now(ZoneOffset.UTC));
		return new EquipmentSparePartRecord(item.getId(), item.getMaterialId(), item.getMaterialCode(), item.getMaterialName(),
				item.getMaterialSpecification(), item.getUnit(), item.getPreferredWarehouseId(), item.getPreferredWarehouseCode(),
				item.getPreferredWarehouseName(), item.getReorderPoint(), available, cost.map(StandardCostProvider.StandardCost::unitCost).orElse(null),
				cost.map(StandardCostProvider.StandardCost::currency).orElse(null), cost.map(StandardCostProvider.StandardCost::effectiveDate).orElse(null),
				cost.isPresent() ? "READY" : "MISSING_COST", available.compareTo(item.getReorderPoint()) <= 0 ? "BELOW_REORDER_POINT" : "SUFFICIENT",
				item.getStatus(), item.getVersion(), item.getUpdatedAt());
	}

	static boolean canMaintain(CurrentWorkspaceAccess access) { return MAINTENANCE_ROLES.contains(access.roleCode()); }
	static void requireMaintain(CurrentWorkspaceAccess access) {
		if (!canMaintain(access)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权维护设备备件与运维成本");
	}
	static String requestId() {
		String value = MDC.get("requestId");
		String normalized = value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
		if (normalized.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id 不能超过 120 个字符");
		return normalized;
	}
	private static String normalize(String value) { return value == null ? "" : value.trim(); }
	private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
	private static ResponseStatusException conflict(String message, Exception cause) { return new ResponseStatusException(HttpStatus.CONFLICT, message, cause); }
}
