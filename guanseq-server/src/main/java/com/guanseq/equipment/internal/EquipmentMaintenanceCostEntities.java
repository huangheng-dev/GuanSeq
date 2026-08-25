package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "equipment", name = "spare_parts")
class EquipmentSparePartEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "creation_request_id") private String creationRequestId;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code_snapshot") private String materialCodeSnapshot;
	@Column(name = "material_name_snapshot") private String materialNameSnapshot;
	@Column(name = "material_specification_snapshot") private String materialSpecificationSnapshot;
	@Column(name = "unit_snapshot") private String unitSnapshot;
	@Column(name = "preferred_warehouse_id") private UUID preferredWarehouseId;
	@Column(name = "preferred_warehouse_code_snapshot") private String preferredWarehouseCodeSnapshot;
	@Column(name = "preferred_warehouse_name_snapshot") private String preferredWarehouseNameSnapshot;
	@Column(name = "reorder_point") private BigDecimal reorderPoint;
	private String status;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentSparePartEntity() { }

	EquipmentSparePartEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String requestId,
			UUID materialId, String materialCode, String materialName, String materialSpecification, String unit,
			UUID warehouseId, String warehouseCode, String warehouseName, BigDecimal reorderPoint, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.creationRequestId = requestId; this.materialId = materialId;
		this.materialCodeSnapshot = materialCode; this.materialNameSnapshot = materialName;
		this.materialSpecificationSnapshot = materialSpecification; this.unitSnapshot = unit;
		this.preferredWarehouseId = warehouseId; this.preferredWarehouseCodeSnapshot = warehouseCode;
		this.preferredWarehouseNameSnapshot = warehouseName; this.reorderPoint = reorderPoint; this.status = "ACTIVE";
		this.createdBy = actorId; this.createdAt = Instant.now(); this.updatedBy = actorId; this.updatedAt = createdAt;
	}

	UUID getId() { return id; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCodeSnapshot; }
	String getMaterialName() { return materialNameSnapshot; }
	String getMaterialSpecification() { return materialSpecificationSnapshot; }
	String getUnit() { return unitSnapshot; }
	UUID getPreferredWarehouseId() { return preferredWarehouseId; }
	String getPreferredWarehouseCode() { return preferredWarehouseCodeSnapshot; }
	String getPreferredWarehouseName() { return preferredWarehouseNameSnapshot; }
	BigDecimal getReorderPoint() { return reorderPoint; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "equipment", name = "maintenance_spare_transactions")
class MaintenanceSpareTransactionEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "work_order_id") private UUID workOrderId;
	@Column(name = "spare_part_id") private UUID sparePartId;
	@Column(name = "transaction_type") private String transactionType;
	@Column(name = "return_of_issue_id") private UUID returnOfIssueId;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code_snapshot") private String materialCodeSnapshot;
	@Column(name = "material_name_snapshot") private String materialNameSnapshot;
	@Column(name = "material_specification_snapshot") private String materialSpecificationSnapshot;
	@Column(name = "unit_snapshot") private String unitSnapshot;
	private BigDecimal quantity;
	@Column(name = "unit_cost") private BigDecimal unitCost;
	private String currency;
	private BigDecimal amount;
	@Column(name = "warehouse_id") private UUID warehouseId;
	@Column(name = "warehouse_code_snapshot") private String warehouseCodeSnapshot;
	@Column(name = "warehouse_name_snapshot") private String warehouseNameSnapshot;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "warehouse_evidence") private List<Map<String, Object>> warehouseEvidence = new ArrayList<>();
	private String reason;
	@Column(name = "request_id") private String requestId;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected MaintenanceSpareTransactionEntity() { }

	MaintenanceSpareTransactionEntity(UUID id, UUID tenantId, UUID workspaceId, UUID actorId, UUID workOrderId,
			EquipmentSparePartEntity spare, String type, UUID returnOfIssueId, BigDecimal quantity, BigDecimal unitCost,
			String currency, BigDecimal amount, UUID warehouseId, String warehouseCode, String warehouseName,
			List<Map<String, Object>> evidence, String reason, String requestId) {
		this.id = id; this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.workOrderId = workOrderId; this.sparePartId = spare.getId();
		this.transactionType = type; this.returnOfIssueId = returnOfIssueId; this.materialId = spare.getMaterialId();
		this.materialCodeSnapshot = spare.getMaterialCode(); this.materialNameSnapshot = spare.getMaterialName();
		this.materialSpecificationSnapshot = spare.getMaterialSpecification(); this.unitSnapshot = spare.getUnit();
		this.quantity = quantity; this.unitCost = unitCost; this.currency = currency; this.amount = amount;
		this.warehouseId = warehouseId; this.warehouseCodeSnapshot = warehouseCode;
		this.warehouseNameSnapshot = warehouseName; this.warehouseEvidence = new ArrayList<>(evidence);
		this.reason = reason.trim(); this.requestId = requestId; this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getActorUserId() { return actorUserId; }
	UUID getWorkOrderId() { return workOrderId; }
	UUID getSparePartId() { return sparePartId; }
	String getTransactionType() { return transactionType; }
	UUID getReturnOfIssueId() { return returnOfIssueId; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCodeSnapshot; }
	String getMaterialName() { return materialNameSnapshot; }
	String getMaterialSpecification() { return materialSpecificationSnapshot; }
	String getUnit() { return unitSnapshot; }
	BigDecimal getQuantity() { return quantity; }
	BigDecimal getUnitCost() { return unitCost; }
	String getCurrency() { return currency; }
	BigDecimal getAmount() { return amount; }
	UUID getWarehouseId() { return warehouseId; }
	String getWarehouseCode() { return warehouseCodeSnapshot; }
	String getWarehouseName() { return warehouseNameSnapshot; }
	List<Map<String, Object>> getWarehouseEvidence() { return warehouseEvidence; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	Instant getOccurredAt() { return occurredAt; }
}

@Entity
@Table(schema = "equipment", name = "maintenance_labor_transactions")
class MaintenanceLaborTransactionEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "work_order_id") private UUID workOrderId;
	@Column(name = "transaction_type") private String transactionType;
	@Column(name = "reversal_of_entry_id") private UUID reversalOfEntryId;
	@Column(name = "technician_name") private String technicianName;
	private BigDecimal hours;
	@Column(name = "hourly_rate") private BigDecimal hourlyRate;
	private String currency;
	private BigDecimal amount;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected MaintenanceLaborTransactionEntity() { }

	MaintenanceLaborTransactionEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID workOrderId, String type,
			UUID reversalOfEntryId, String technicianName, BigDecimal hours, BigDecimal hourlyRate, String currency,
			BigDecimal amount, String reason, String requestId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.workOrderId = workOrderId; this.transactionType = type;
		this.reversalOfEntryId = reversalOfEntryId; this.technicianName = technicianName.trim(); this.hours = hours;
		this.hourlyRate = hourlyRate; this.currency = currency; this.amount = amount; this.reason = reason.trim();
		this.requestId = requestId; this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getActorUserId() { return actorUserId; }
	UUID getWorkOrderId() { return workOrderId; }
	String getTransactionType() { return transactionType; }
	UUID getReversalOfEntryId() { return reversalOfEntryId; }
	String getTechnicianName() { return technicianName; }
	BigDecimal getHours() { return hours; }
	BigDecimal getHourlyRate() { return hourlyRate; }
	String getCurrency() { return currency; }
	BigDecimal getAmount() { return amount; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	Instant getOccurredAt() { return occurredAt; }
}
