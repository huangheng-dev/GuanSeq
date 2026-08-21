package com.guanseq.production.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.guanseq.masterdata.api.MasterDataReferenceProvider.MaterialReference;

@Entity
@Table(schema = "production", name = "production_orders")
class ProductionOrderEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "planned_quantity") private BigDecimal plannedQuantity;
	@Column(name = "completed_quantity") private BigDecimal completedQuantity;
	@Column(name = "reported_quantity") private BigDecimal reportedQuantity;
	@Column(name = "planned_start_date") private LocalDate plannedStartDate;
	@Column(name = "planned_receipt_date") private LocalDate plannedReceiptDate;
	private String workshop;
	private String owner;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "source_id") private UUID sourceId;
	@Column(name = "source_number") private String sourceNumber;
	private String status;
	@Column(name = "cancellation_reason") private String cancellationReason;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected ProductionOrderEntity() { }

	ProductionOrderEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String orderNumber, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.orderNumber = orderNumber; this.completedQuantity = BigDecimal.ZERO;
		this.reportedQuantity = BigDecimal.ZERO;
		this.status = "DRAFT"; this.createdBy = actorId; this.createdAt = Instant.now();
		this.updatedBy = actorId; this.updatedAt = createdAt;
	}

	void update(MaterialReference material, BigDecimal quantity, LocalDate startDate, LocalDate receiptDate,
			String workshop, String owner, String sourceType, UUID sourceId, String sourceNumber, UUID actorId) {
		this.materialId = material.id(); this.materialCode = material.code(); this.materialName = material.name();
		this.materialSpecification = material.specification(); this.unit = material.baseUnit();
		this.plannedQuantity = quantity; this.plannedStartDate = startDate; this.plannedReceiptDate = receiptDate;
		this.workshop = workshop.trim(); this.owner = owner.trim(); this.sourceType = sourceType;
		this.sourceId = sourceId; this.sourceNumber = sourceNumber == null || sourceNumber.isBlank() ? null : sourceNumber.trim();
		this.updatedBy = actorId; this.updatedAt = Instant.now();
	}

	void transition(String target, String comment, UUID actorId) {
		this.status = target; this.cancellationReason = "CANCELLED".equals(target) ? comment : null;
		this.updatedBy = actorId; this.updatedAt = Instant.now();
	}

	void reserveReport(BigDecimal quantity, UUID actorId) {
		if (getReportableQuantity().compareTo(quantity) < 0) throw new IllegalStateException("报工数量超过当前可报数量");
		this.reportedQuantity = this.reportedQuantity.add(quantity); this.updatedBy = actorId; this.updatedAt = Instant.now();
	}

	void settleReport(BigDecimal reported, BigDecimal accepted, UUID actorId) {
		this.reportedQuantity = this.reportedQuantity.subtract(reported);
		this.completedQuantity = this.completedQuantity.add(accepted);
		if (this.completedQuantity.compareTo(this.plannedQuantity) == 0 && this.reportedQuantity.signum() == 0) this.status = "COMPLETED";
		this.updatedBy = actorId; this.updatedAt = Instant.now();
	}

	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getOrderNumber() { return orderNumber; } UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; } String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; } String getUnit() { return unit; }
	BigDecimal getPlannedQuantity() { return plannedQuantity; } BigDecimal getCompletedQuantity() { return completedQuantity; }
	BigDecimal getReportedQuantity() { return reportedQuantity; }
	BigDecimal getReportableQuantity() { return plannedQuantity.subtract(completedQuantity).subtract(reportedQuantity); }
	BigDecimal getOutstandingQuantity() { return plannedQuantity.subtract(completedQuantity); }
	LocalDate getPlannedStartDate() { return plannedStartDate; } LocalDate getPlannedReceiptDate() { return plannedReceiptDate; }
	String getWorkshop() { return workshop; } String getOwner() { return owner; } String getSourceType() { return sourceType; }
	UUID getSourceId() { return sourceId; } String getSourceNumber() { return sourceNumber; } String getStatus() { return status; }
	String getCancellationReason() { return cancellationReason; } long getVersion() { return version; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "production", name = "production_order_events")
class ProductionOrderEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "order_id") private UUID orderId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	private String comment;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected ProductionOrderEventEntity() { }
	ProductionOrderEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID orderId, String action,
			String from, String to, String requestId, String comment, Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.orderId = orderId; this.action = action; this.fromStatus = from;
		this.toStatus = to; this.requestId = requestId; this.comment = comment; this.details = details;
		this.occurredAt = Instant.now();
	}
}
