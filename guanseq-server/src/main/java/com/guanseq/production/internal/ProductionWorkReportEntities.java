package com.guanseq.production.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "production", name = "work_reports")
class ProductionWorkReportEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "report_number") private String reportNumber;
	@Column(name = "order_id") private UUID orderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	private String workshop;
	@Column(name = "shift_name") private String shiftName;
	@Column(name = "operator_name") private String operatorName;
	@Column(name = "reported_quantity") private BigDecimal reportedQuantity;
	private String note;
	@Column(name = "inspection_id") private UUID inspectionId;
	@Column(name = "accepted_quantity") private BigDecimal acceptedQuantity;
	@Column(name = "rejected_quantity") private BigDecimal rejectedQuantity;
	@Column(name = "receipt_balance_id") private UUID receiptBalanceId;
	@Column(name = "receipt_movement_id") private UUID receiptMovementId;
	@Column(name = "lot_number") private String lotNumber;
	private String status;
	@Column(name = "operation_task_id") private UUID operationTaskId;
	private String source;
	@Column(name = "request_id") private String requestId;
	@Column(name = "settlement_request_id") private String settlementRequestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "settled_by") private UUID settledBy;
	@Column(name = "settled_at") private Instant settledAt;

	protected ProductionWorkReportEntity() { }
	ProductionWorkReportEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String number,
			ProductionOrderEntity order, BigDecimal quantity, String shift, String operator, String note,
			String requestId, UUID actorId, UUID operationTaskId, String source) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.reportNumber = number; this.orderId = order.getId();
		this.orderNumber = order.getOrderNumber(); this.materialId = order.getMaterialId(); this.materialCode = order.getMaterialCode();
		this.materialName = order.getMaterialName(); this.materialSpecification = order.getMaterialSpecification();
		this.unit = order.getUnit(); this.workshop = order.getWorkshop(); this.shiftName = shift.trim();
		this.operatorName = operator.trim(); this.reportedQuantity = quantity;
		this.note = note == null || note.isBlank() ? null : note.trim(); this.status = "PENDING_INSPECTION";
		this.operationTaskId = operationTaskId; this.source = source;
		this.requestId = requestId; this.createdBy = actorId; this.createdAt = Instant.now();
	}
	void attachInspection(UUID id) { this.inspectionId = id; }
	void settle(BigDecimal accepted, BigDecimal rejected, UUID balanceId, UUID movementId, String lot,
			String requestId, UUID actorId) {
		this.acceptedQuantity = accepted; this.rejectedQuantity = rejected; this.receiptBalanceId = balanceId;
		this.receiptMovementId = movementId; this.lotNumber = lot; this.status = accepted.signum() > 0 ? "RECEIVED" : "REJECTED_CLOSED";
		this.settlementRequestId = requestId; this.settledBy = actorId; this.settledAt = Instant.now();
	}

	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; } UUID getWorkspaceId() { return workspaceId; }
	String getReportNumber() { return reportNumber; } UUID getOrderId() { return orderId; } String getOrderNumber() { return orderNumber; }
	UUID getMaterialId() { return materialId; } String getMaterialCode() { return materialCode; } String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; } String getUnit() { return unit; } String getWorkshop() { return workshop; }
	String getShiftName() { return shiftName; } String getOperatorName() { return operatorName; } BigDecimal getReportedQuantity() { return reportedQuantity; }
	String getNote() { return note; } UUID getInspectionId() { return inspectionId; } BigDecimal getAcceptedQuantity() { return acceptedQuantity; }
	BigDecimal getRejectedQuantity() { return rejectedQuantity; } UUID getReceiptBalanceId() { return receiptBalanceId; }
	UUID getReceiptMovementId() { return receiptMovementId; } String getLotNumber() { return lotNumber; } String getStatus() { return status; }
	UUID getOperationTaskId() { return operationTaskId; } String getSource() { return source; }
	long getVersion() { return version; } Instant getCreatedAt() { return createdAt; } Instant getSettledAt() { return settledAt; }
}
