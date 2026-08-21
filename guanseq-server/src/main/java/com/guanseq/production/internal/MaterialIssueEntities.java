package com.guanseq.production.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.guanseq.product.api.BomReferenceProvider.Component;
import com.guanseq.production.api.MaterialReturnRecord;
import com.guanseq.warehouse.api.WarehouseReferenceProvider.WarehouseOption;

@Entity
@Table(schema = "production", name = "material_issues")
class MaterialIssueEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "issue_number") private String issueNumber;
	@Column(name = "production_order_id") private UUID productionOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "planned_quantity") private BigDecimal plannedQuantity;
	@Column(name = "warehouse_id") private UUID warehouseId;
	@Column(name = "warehouse_code") private String warehouseCode;
	@Column(name = "warehouse_name") private String warehouseName;
	private String status;
	@Column(name = "cancellation_reason") private String cancellationReason;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected MaterialIssueEntity() { }

	MaterialIssueEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String issueNumber,
			ProductionOrderEntity order, WarehouseOption warehouse, String requestId, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.issueNumber = issueNumber; this.productionOrderId = order.getId();
		this.orderNumber = order.getOrderNumber(); this.materialId = order.getMaterialId();
		this.materialCode = order.getMaterialCode(); this.materialName = order.getMaterialName();
		this.materialSpecification = order.getMaterialSpecification(); this.unit = order.getUnit();
		this.plannedQuantity = order.getPlannedQuantity(); this.warehouseId = warehouse.id();
		this.warehouseCode = warehouse.code(); this.warehouseName = warehouse.name();
		this.status = "DRAFT"; this.requestId = requestId; this.createdBy = actorId;
		this.createdAt = Instant.now(); this.updatedBy = actorId; this.updatedAt = createdAt;
	}

	MaterialIssueLineEntity createLine(int lineNumber, Component component, BigDecimal requiredQuantity, UUID actorId) {
		return new MaterialIssueLineEntity(tenantOrganizationId, id, lineNumber, component, requiredQuantity, actorId);
	}

	void markIssuedIfComplete(java.util.List<MaterialIssueLineEntity> lines, UUID actorId) {
		boolean anyIssued = lines.stream().anyMatch(line -> line.getIssuedQuantity().signum() > 0);
		boolean allComplete = lines.stream().allMatch(line -> line.getIssuedQuantity().compareTo(line.getRequiredQuantity()) == 0);
		if (!anyIssued) return;
		status = allComplete ? "ISSUED" : "PARTIAL";
		touch(actorId);
	}

	void cancel(String reason, UUID actorId) {
		if (!"DRAFT".equals(status)) throw new IllegalStateException("只有未发料的草稿领料单可以取消");
		this.status = "CANCELLED"; this.cancellationReason = reason; touch(actorId);
	}

	private void touch(UUID actorId) { this.updatedBy = actorId; this.updatedAt = Instant.now(); }

	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; } UUID getWorkspaceId() { return workspaceId; }
	String getIssueNumber() { return issueNumber; } UUID getProductionOrderId() { return productionOrderId; }
	String getOrderNumber() { return orderNumber; } UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; } String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; } String getUnit() { return unit; }
	BigDecimal getPlannedQuantity() { return plannedQuantity; } UUID getWarehouseId() { return warehouseId; }
	String getWarehouseCode() { return warehouseCode; } String getWarehouseName() { return warehouseName; }
	String getStatus() { return status; } String getCancellationReason() { return cancellationReason; }
	String getRequestId() { return requestId; } long getVersion() { return version; }
	UUID getCreatedBy() { return createdBy; } Instant getCreatedAt() { return createdAt; }
	UUID getUpdatedBy() { return updatedBy; } Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "production", name = "material_issue_lines")
class MaterialIssueLineEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "issue_id") private UUID issueId;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "component_material_id") private UUID componentMaterialId;
	@Column(name = "component_material_code") private String componentMaterialCode;
	@Column(name = "component_material_name") private String componentMaterialName;
	@Column(name = "component_material_specification") private String componentMaterialSpecification;
	private String unit;
	@Column(name = "required_quantity") private BigDecimal requiredQuantity;
	@Column(name = "issued_quantity") private BigDecimal issuedQuantity;
	@Column(name = "returned_quantity") private BigDecimal returnedQuantity;
	@Column(name = "bom_note") private String bomNote;
	@Version private long version;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected MaterialIssueLineEntity() { }

	MaterialIssueLineEntity(UUID tenantId, UUID issueId, int lineNumber, Component component, BigDecimal requiredQuantity, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.issueId = issueId; this.lineNumber = lineNumber;
		this.componentMaterialId = component.materialId(); this.componentMaterialCode = component.materialCode();
		this.componentMaterialName = component.materialName(); this.unit = component.unit();
		this.componentMaterialSpecification = null; this.requiredQuantity = requiredQuantity;
		this.issuedQuantity = BigDecimal.ZERO; this.returnedQuantity = BigDecimal.ZERO; this.bomNote = null;
		this.updatedBy = actorId; this.updatedAt = Instant.now();
	}

	void issue(BigDecimal quantity, UUID actorId) {
		if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("发料数量必须大于零");
		if (issuedQuantity.add(quantity).compareTo(requiredQuantity) > 0) throw new IllegalStateException("发料数量不能超过需求数量");
		issuedQuantity = issuedQuantity.add(quantity); updatedBy = actorId; updatedAt = Instant.now();
	}

	void returnMaterial(BigDecimal quantity, UUID actorId) {
		if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("退料数量必须大于零");
		if (returnedQuantity.add(quantity).compareTo(issuedQuantity) > 0) throw new IllegalStateException("退料数量不能超过已领数量");
		returnedQuantity = returnedQuantity.add(quantity); updatedBy = actorId; updatedAt = Instant.now();
	}

	BigDecimal issuableQuantity() { return requiredQuantity.subtract(issuedQuantity); }

	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getIssueId() { return issueId; } int getLineNumber() { return lineNumber; }
	UUID getComponentMaterialId() { return componentMaterialId; } String getComponentMaterialCode() { return componentMaterialCode; }
	String getComponentMaterialName() { return componentMaterialName; } String getComponentMaterialSpecification() { return componentMaterialSpecification; }
	String getUnit() { return unit; } BigDecimal getRequiredQuantity() { return requiredQuantity; }
	BigDecimal getIssuedQuantity() { return issuedQuantity; } BigDecimal getReturnedQuantity() { return returnedQuantity; }
	String getBomNote() { return bomNote; } long getVersion() { return version; } Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "production", name = "material_issue_events")
class MaterialIssueEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "issue_id") private UUID issueId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	private String comment;
	@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
	private java.util.Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected MaterialIssueEventEntity() { }
	MaterialIssueEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID issueId, String action, String fromStatus,
			String toStatus, String requestId, String comment, java.util.Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.issueId = issueId; this.action = action; this.fromStatus = fromStatus;
		this.toStatus = toStatus; this.requestId = requestId; this.comment = comment;
		this.details = details == null ? java.util.Map.of() : details; this.occurredAt = Instant.now();
	}
	UUID getId() { return id; } UUID getIssueId() { return issueId; } String getAction() { return action; } String getFromStatus() { return fromStatus; }
	String getToStatus() { return toStatus; } String getRequestId() { return requestId; } Instant getOccurredAt() { return occurredAt; }
}

@Entity
@Table(schema = "production", name = "material_returns")
class MaterialReturnEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "return_number") private String returnNumber;
	@Column(name = "issue_id") private UUID issueId;
	@Column(name = "issue_number") private String issueNumber;
	@Column(name = "production_order_id") private UUID productionOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "warehouse_id") private UUID warehouseId;
	@Column(name = "warehouse_code") private String warehouseCode;
	@Column(name = "warehouse_name") private String warehouseName;
	@Column(name = "location_id") private UUID locationId;
	@Column(name = "location_code") private String locationCode;
	@Column(name = "location_name") private String locationName;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;

	protected MaterialReturnEntity() { }
	MaterialReturnEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String returnNumber, MaterialIssueEntity issue,
			com.guanseq.warehouse.api.WarehouseReferenceProvider.LocationOption location, String reason, String requestId, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.returnNumber = returnNumber; this.issueId = issue.getId();
		this.issueNumber = issue.getIssueNumber(); this.productionOrderId = issue.getProductionOrderId();
		this.orderNumber = issue.getOrderNumber(); this.warehouseId = issue.getWarehouseId();
		this.warehouseCode = issue.getWarehouseCode(); this.warehouseName = issue.getWarehouseName();
		this.locationId = location.id(); this.locationCode = location.code(); this.locationName = location.name();
		this.reason = reason; this.requestId = requestId; this.createdBy = actorId; this.createdAt = Instant.now();
	}
	MaterialReturnRecord.Line toLine(MaterialReturnLineEntity line) {
		return new MaterialReturnRecord.Line(line.getId(), line.getIssueLineId(), line.getLineNumber(),
				line.getComponentMaterialId(), line.getComponentMaterialCode(), line.getComponentMaterialName(),
				line.getComponentMaterialSpecification(), line.getUnit(), line.getQuantity(), line.getReason());
	}
	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; } UUID getWorkspaceId() { return workspaceId; }
	String getReturnNumber() { return returnNumber; } UUID getIssueId() { return issueId; } String getIssueNumber() { return issueNumber; }
	UUID getProductionOrderId() { return productionOrderId; } String getOrderNumber() { return orderNumber; }
	UUID getWarehouseId() { return warehouseId; } String getWarehouseCode() { return warehouseCode; } String getWarehouseName() { return warehouseName; }
	UUID getLocationId() { return locationId; } String getLocationCode() { return locationCode; } String getLocationName() { return locationName; }
	String getReason() { return reason; } String getRequestId() { return requestId; } Instant getCreatedAt() { return createdAt; }
}

@Entity
@Table(schema = "production", name = "material_return_lines")
class MaterialReturnLineEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "return_id") private UUID returnId;
	@Column(name = "issue_line_id") private UUID issueLineId;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "component_material_id") private UUID componentMaterialId;
	@Column(name = "component_material_code") private String componentMaterialCode;
	@Column(name = "component_material_name") private String componentMaterialName;
	@Column(name = "component_material_specification") private String componentMaterialSpecification;
	private String unit;
	private BigDecimal quantity;
	private String reason;

	protected MaterialReturnLineEntity() { }
	MaterialReturnLineEntity(UUID tenantId, UUID returnId, MaterialIssueLineEntity issueLine, BigDecimal quantity, String reason) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.returnId = returnId;
		this.issueLineId = issueLine.getId(); this.lineNumber = issueLine.getLineNumber();
		this.componentMaterialId = issueLine.getComponentMaterialId(); this.componentMaterialCode = issueLine.getComponentMaterialCode();
		this.componentMaterialName = issueLine.getComponentMaterialName(); this.componentMaterialSpecification = issueLine.getComponentMaterialSpecification();
		this.unit = issueLine.getUnit(); this.quantity = quantity; this.reason = reason;
	}
	UUID getId() { return id; } UUID getIssueLineId() { return issueLineId; } int getLineNumber() { return lineNumber; }
	UUID getComponentMaterialId() { return componentMaterialId; } String getComponentMaterialCode() { return componentMaterialCode; }
	String getComponentMaterialName() { return componentMaterialName; } String getComponentMaterialSpecification() { return componentMaterialSpecification; }
	String getUnit() { return unit; } BigDecimal getQuantity() { return quantity; } String getReason() { return reason; }
}

@Entity
@Table(schema = "production", name = "material_stock_transactions")
class MaterialStockTransactionEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "issue_id") private UUID issueId;
	@Column(name = "issue_line_id") private UUID issueLineId;
	@Column(name = "return_id") private UUID returnId;
	@Column(name = "return_line_id") private UUID returnLineId;
	@Column(name = "movement_type") private String movementType;
	@Column(name = "component_material_id") private UUID componentMaterialId;
	@Column(name = "component_material_code") private String componentMaterialCode;
	private BigDecimal quantity;
	@Column(name = "warehouse_id") private UUID warehouseId;
	@Column(name = "warehouse_code") private String warehouseCode;
	@Column(name = "warehouse_name") private String warehouseName;
	@Column(name = "location_id") private UUID locationId;
	@Column(name = "location_code") private String locationCode;
	@Column(name = "location_name") private String locationName;
	@Column(name = "balance_id") private UUID balanceId;
	@Column(name = "movement_id") private UUID movementId;
	@Column(name = "movement_number") private String movementNumber;
	@Column(name = "request_id") private String requestId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected MaterialStockTransactionEntity() { }
	MaterialStockTransactionEntity(UUID tenantId, UUID issueId, UUID issueLineId, UUID returnId, UUID returnLineId,
			String movementType, UUID componentMaterialId, String componentMaterialCode, BigDecimal quantity,
			com.guanseq.warehouse.api.ProductionMaterialStockService.StockMovementResult movement, String requestId, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.issueId = issueId; this.issueLineId = issueLineId;
		this.returnId = returnId; this.returnLineId = returnLineId; this.movementType = movementType;
		this.componentMaterialId = componentMaterialId; this.componentMaterialCode = componentMaterialCode;
		this.quantity = quantity; this.warehouseId = movement.warehouseId(); this.warehouseCode = movement.warehouseCode();
		this.warehouseName = movement.warehouseName(); this.locationId = movement.locationId(); this.locationCode = movement.locationCode();
		this.locationName = movement.locationName(); this.balanceId = movement.balanceId(); this.movementId = movement.movementId();
		this.movementNumber = movement.movementNumber(); this.requestId = requestId; this.actorUserId = actorId; this.occurredAt = Instant.now();
	}
	UUID getId() { return id; } UUID getIssueLineId() { return issueLineId; } UUID getReturnLineId() { return returnLineId; }
	String getMovementType() { return movementType; } String getComponentMaterialCode() { return componentMaterialCode; }
	BigDecimal getQuantity() { return quantity; } UUID getWarehouseId() { return warehouseId; } String getWarehouseCode() { return warehouseCode; }
	String getWarehouseName() { return warehouseName; } UUID getLocationId() { return locationId; } String getLocationCode() { return locationCode; }
	String getLocationName() { return locationName; } UUID getMovementId() { return movementId; } String getMovementNumber() { return movementNumber; }
	String getRequestId() { return requestId; } Instant getOccurredAt() { return occurredAt; }
}
