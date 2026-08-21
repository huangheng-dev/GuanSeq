package com.guanseq.quality.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "quality", name = "inspections")
class FinalInspectionEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "inspection_number") private String inspectionNumber;
	@Column(name = "inspection_type") private String inspectionType;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "source_id") private UUID sourceId;
	@Column(name = "source_number") private String sourceNumber;
	@Column(name = "order_id") private UUID orderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "supplier_id") private UUID supplierId;
	@Column(name = "supplier_code") private String supplierCode;
	@Column(name = "supplier_name") private String supplierName;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "inspection_quantity") private BigDecimal inspectionQuantity;
	private String status;
	private String result;
	@Column(name = "accepted_quantity") private BigDecimal acceptedQuantity;
	@Column(name = "rejected_quantity") private BigDecimal rejectedQuantity;
	private String inspector;
	@Column(name = "defect_description") private String defectDescription;
	private String conclusion;
	@Column(name = "request_id") private String requestId;
	@Column(name = "decision_request_id") private String decisionRequestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "completed_by") private UUID completedBy;
	@Column(name = "completed_at") private Instant completedAt;

	protected FinalInspectionEntity() { }

	FinalInspectionEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String number, UUID actorId,
			UUID sourceId, String sourceNumber, UUID orderId, String orderNumber, UUID supplierId, String supplierCode,
			String supplierName, UUID materialId, String materialCode, String materialName, String materialSpecification,
			String unit, BigDecimal quantity, String requestId) {
		this(tenantId, organizationId, workspaceId, number, actorId, sourceId, sourceNumber, orderId, orderNumber,
				materialId, materialCode, materialName, materialSpecification, unit, quantity, requestId);
		this.inspectionType = "INCOMING"; this.sourceType = "PURCHASE_RECEIPT_LINE";
		this.supplierId = supplierId; this.supplierCode = supplierCode; this.supplierName = supplierName;
	}

	FinalInspectionEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String number, UUID actorId,
			UUID sourceId, String sourceNumber, UUID orderId, String orderNumber, UUID materialId,
			String materialCode, String materialName, String materialSpecification, String unit,
			BigDecimal quantity, String requestId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.inspectionNumber = number; this.inspectionType = "FINAL";
		this.sourceType = "PRODUCTION_REPORT"; this.sourceId = sourceId; this.sourceNumber = sourceNumber;
		this.orderId = orderId; this.orderNumber = orderNumber; this.materialId = materialId;
		this.materialCode = materialCode; this.materialName = materialName;
		this.materialSpecification = materialSpecification; this.unit = unit; this.inspectionQuantity = quantity;
		this.status = "PENDING"; this.requestId = requestId; this.createdBy = actorId; this.createdAt = Instant.now();
	}

	void complete(BigDecimal accepted, BigDecimal rejected, String inspector, String defect, String conclusion,
			String decisionRequestId, UUID actorId) {
		this.acceptedQuantity = accepted; this.rejectedQuantity = rejected;
		this.result = rejected.signum() == 0 ? "PASSED" : accepted.signum() == 0 ? "FAILED" : "PARTIALLY_PASSED";
		this.inspector = inspector.trim(); this.defectDescription = defect == null || defect.isBlank() ? null : defect.trim();
		this.conclusion = conclusion.trim(); this.decisionRequestId = decisionRequestId; this.status = "COMPLETED";
		this.completedBy = actorId; this.completedAt = Instant.now();
	}

	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getInspectionType() { return inspectionType; }
	String getInspectionNumber() { return inspectionNumber; } String getSourceType() { return sourceType; }
	UUID getSourceId() { return sourceId; } String getSourceNumber() { return sourceNumber; }
	UUID getOrderId() { return orderId; } String getOrderNumber() { return orderNumber; }
	UUID getSupplierId() { return supplierId; } String getSupplierCode() { return supplierCode; } String getSupplierName() { return supplierName; }
	UUID getMaterialId() { return materialId; } String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; } String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; } BigDecimal getInspectionQuantity() { return inspectionQuantity; }
	String getStatus() { return status; } String getResult() { return result; }
	BigDecimal getAcceptedQuantity() { return acceptedQuantity; } BigDecimal getRejectedQuantity() { return rejectedQuantity; }
	String getInspector() { return inspector; } String getDefectDescription() { return defectDescription; }
	String getConclusion() { return conclusion; } String getDecisionRequestId() { return decisionRequestId; }
	long getVersion() { return version; } Instant getCreatedAt() { return createdAt; } Instant getCompletedAt() { return completedAt; }
}

@Entity
@Table(schema = "quality", name = "inspection_events")
class FinalInspectionEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "inspection_id") private UUID inspectionId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;
	protected FinalInspectionEventEntity() { }
	FinalInspectionEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID inspectionId, String action,
			String from, String to, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.inspectionId = inspectionId; this.action = action; this.fromStatus = from;
		this.toStatus = to; this.requestId = requestId; this.details = details; this.occurredAt = Instant.now();
	}
}
