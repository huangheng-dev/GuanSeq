package com.guanseq.warehouse.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "warehouse", name = "warehouses")
class WarehouseEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "operating_organization_id") private UUID operatingOrganizationId;
	private String code;
	private String name;
	private String status;
	protected WarehouseEntity() { }
	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getCode() { return code; }
	String getName() { return name; }
}

@Entity
@Table(schema = "warehouse", name = "storage_locations")
class StorageLocationEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "warehouse_id") private UUID warehouseId;
	private String code;
	private String name;
	@Column(name = "location_type") private String locationType;
	private String status;
	protected StorageLocationEntity() { }
	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getWarehouseId() { return warehouseId; }
	String getCode() { return code; }
	String getName() { return name; }
	String getLocationType() { return locationType; }
}

@Entity
@Table(schema = "warehouse", name = "stock_balances")
class StockBalanceEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "warehouse_id") private UUID warehouseId;
	@Column(name = "warehouse_code") private String warehouseCode;
	@Column(name = "warehouse_name") private String warehouseName;
	@Column(name = "location_id") private UUID locationId;
	@Column(name = "location_code") private String locationCode;
	@Column(name = "location_name") private String locationName;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "lot_number") private String lotNumber;
	@Column(name = "quality_status") private String qualityStatus;
	@Column(name = "on_hand_quantity") private BigDecimal onHandQuantity;
	@Column(name = "allocated_quantity") private BigDecimal allocatedQuantity;
	@Column(name = "frozen_quantity") private BigDecimal frozenQuantity;
	@Version private long version;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected StockBalanceEntity() { }

	StockBalanceEntity(UUID tenantId, UUID organizationId, UUID workspaceId, WarehouseEntity warehouse,
			StorageLocationEntity location, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, String lotNumber, UUID actorId) {
		this(tenantId, organizationId, workspaceId, warehouse, location, materialId, materialCode, materialName,
				materialSpecification, unit, lotNumber, "AVAILABLE", actorId);
	}

	StockBalanceEntity(UUID tenantId, UUID organizationId, UUID workspaceId, WarehouseEntity warehouse,
			StorageLocationEntity location, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, String lotNumber, String qualityStatus, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.warehouseId = warehouse.getId(); this.warehouseCode = warehouse.getCode();
		this.warehouseName = warehouse.getName(); this.locationId = location.getId(); this.locationCode = location.getCode();
		this.locationName = location.getName(); this.materialId = materialId; this.materialCode = materialCode;
		this.materialName = materialName; this.materialSpecification = materialSpecification; this.unit = unit;
		this.lotNumber = lotNumber; this.qualityStatus = qualityStatus; this.onHandQuantity = BigDecimal.ZERO;
		this.allocatedQuantity = BigDecimal.ZERO; this.frozenQuantity = BigDecimal.ZERO;
		this.updatedBy = actorId; this.updatedAt = Instant.now();
	}

	Change apply(String type, BigDecimal quantity, UUID actorId) {
		BigDecimal beforeOnHand = onHandQuantity;
		BigDecimal beforeAllocated = allocatedQuantity;
		BigDecimal beforeFrozen = frozenQuantity;
		switch (type) {
			case "RECEIPT", "RETURN" -> onHandQuantity = onHandQuantity.add(quantity);
			case "ISSUE" -> onHandQuantity = onHandQuantity.subtract(quantity);
			case "ALLOCATE" -> allocatedQuantity = allocatedQuantity.add(quantity);
			case "DEALLOCATE" -> allocatedQuantity = allocatedQuantity.subtract(quantity);
			case "FREEZE" -> frozenQuantity = frozenQuantity.add(quantity);
			case "UNFREEZE" -> frozenQuantity = frozenQuantity.subtract(quantity);
			default -> throw new IllegalArgumentException("Unsupported movement type");
		}
		if (onHandQuantity.signum() < 0 || allocatedQuantity.signum() < 0 || frozenQuantity.signum() < 0
				|| allocatedQuantity.add(frozenQuantity).compareTo(onHandQuantity) > 0) {
			onHandQuantity = beforeOnHand;
			allocatedQuantity = beforeAllocated;
			frozenQuantity = beforeFrozen;
			throw new IllegalStateException("库存数量边界不允许该事务");
		}
		updatedBy = actorId;
		updatedAt = Instant.now();
		return new Change(beforeOnHand, onHandQuantity, beforeAllocated, allocatedQuantity, beforeFrozen, frozenQuantity);
	}

	BigDecimal availableQuantity() {
		if (!"AVAILABLE".equals(qualityStatus)) return BigDecimal.ZERO;
		return onHandQuantity.subtract(allocatedQuantity).subtract(frozenQuantity);
	}

	record Change(BigDecimal beforeOnHand, BigDecimal afterOnHand, BigDecimal beforeAllocated,
			BigDecimal afterAllocated, BigDecimal beforeFrozen, BigDecimal afterFrozen) { }

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	UUID getWarehouseId() { return warehouseId; }
	String getWarehouseCode() { return warehouseCode; }
	String getWarehouseName() { return warehouseName; }
	UUID getLocationId() { return locationId; }
	String getLocationCode() { return locationCode; }
	String getLocationName() { return locationName; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	String getLotNumber() { return lotNumber; }
	String getQualityStatus() { return qualityStatus; }
	BigDecimal getOnHandQuantity() { return onHandQuantity; }
	BigDecimal getAllocatedQuantity() { return allocatedQuantity; }
	BigDecimal getFrozenQuantity() { return frozenQuantity; }
	long getVersion() { return version; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "warehouse", name = "stock_movements")
class StockMovementEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "balance_id") private UUID balanceId;
	@Column(name = "movement_number") private String movementNumber;
	@Column(name = "movement_type") private String movementType;
	private BigDecimal quantity;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@Column(name = "before_on_hand") private BigDecimal beforeOnHand;
	@Column(name = "after_on_hand") private BigDecimal afterOnHand;
	@Column(name = "before_allocated") private BigDecimal beforeAllocated;
	@Column(name = "after_allocated") private BigDecimal afterAllocated;
	@Column(name = "before_frozen") private BigDecimal beforeFrozen;
	@Column(name = "after_frozen") private BigDecimal afterFrozen;
	@Column(name = "occurred_at") private Instant occurredAt;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "source_id") private UUID sourceId;
	@Column(name = "source_number") private String sourceNumber;
	@Column(name = "source_line_id") private UUID sourceLineId;

	protected StockMovementEntity() { }

	StockMovementEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID balanceId, String movementNumber,
			String movementType, BigDecimal quantity, String reason, String requestId, StockBalanceEntity.Change change) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.balanceId = balanceId; this.movementNumber = movementNumber;
		this.movementType = movementType; this.quantity = quantity; this.reason = reason.trim(); this.requestId = requestId;
		this.beforeOnHand = change.beforeOnHand(); this.afterOnHand = change.afterOnHand();
		this.beforeAllocated = change.beforeAllocated(); this.afterAllocated = change.afterAllocated();
		this.beforeFrozen = change.beforeFrozen(); this.afterFrozen = change.afterFrozen(); this.occurredAt = Instant.now();
	}

	void attachSource(String sourceType, UUID sourceId, String sourceNumber, UUID sourceLineId) {
		this.sourceType = sourceType; this.sourceId = sourceId; this.sourceNumber = sourceNumber; this.sourceLineId = sourceLineId;
	}

	UUID getId() { return id; }
	UUID getBalanceId() { return balanceId; }
	String getMovementNumber() { return movementNumber; }
	String getMovementType() { return movementType; }
	BigDecimal getQuantity() { return quantity; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	BigDecimal getBeforeOnHand() { return beforeOnHand; }
	BigDecimal getAfterOnHand() { return afterOnHand; }
	BigDecimal getBeforeAllocated() { return beforeAllocated; }
	BigDecimal getAfterAllocated() { return afterAllocated; }
	BigDecimal getBeforeFrozen() { return beforeFrozen; }
	BigDecimal getAfterFrozen() { return afterFrozen; }
	Instant getOccurredAt() { return occurredAt; }
	UUID getSourceId() { return sourceId; }
}


