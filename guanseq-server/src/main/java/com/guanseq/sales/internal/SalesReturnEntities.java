package com.guanseq.sales.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "sales", name = "returns")
class SalesReturnEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "return_number") private String returnNumber;
	@Column(name = "sales_order_id") private UUID salesOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "customer_code") private String customerCode;
	@Column(name = "customer_name") private String customerName;
	@Column(name = "return_date") private LocalDate returnDate;
	private String status;
	private String reason;
	private String note;
	@Column(name = "warehouse_id") private UUID warehouseId;
	@Column(name = "warehouse_code") private String warehouseCode;
	@Column(name = "warehouse_name") private String warehouseName;
	@Column(name = "location_id") private UUID locationId;
	@Column(name = "location_code") private String locationCode;
	@Column(name = "location_name") private String locationName;
	@Column(name = "total_return_quantity") private BigDecimal totalReturnQuantity;
	@Column(name = "received_at") private Instant receivedAt;
	@Column(name = "inspected_at") private Instant inspectedAt;
	@Column(name = "completed_by") private UUID completedBy;
	@Column(name = "create_request_id") private String createRequestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<SalesReturnLineEntity> lines = new ArrayList<>();

	protected SalesReturnEntity() { }

	SalesReturnEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, String returnNumber,
			SalesOrderEntity order, LocalDate returnDate, String reason, String note, BigDecimal totalReturnQuantity,
			String createRequestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.returnNumber = returnNumber;
		this.salesOrderId = order.getId();
		this.orderNumber = order.getOrderNumber();
		this.customerId = order.getCustomerId();
		this.customerCode = order.getCustomerCode();
		this.customerName = order.getCustomerName();
		this.returnDate = returnDate;
		this.status = "PENDING_RECEIPT";
		this.reason = reason.trim();
		this.note = note == null || note.isBlank() ? null : note.trim();
		this.totalReturnQuantity = totalReturnQuantity.setScale(4, RoundingMode.HALF_UP);
		this.createRequestId = createRequestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void addLine(SalesReturnLineEntity line) { lines.add(line); }
	void markReceived(UUID warehouseId, String warehouseCode, String warehouseName, UUID locationId,
			String locationCode, String locationName, UUID actorUserId) {
		this.warehouseId = warehouseId; this.warehouseCode = warehouseCode; this.warehouseName = warehouseName;
		this.locationId = locationId; this.locationCode = locationCode; this.locationName = locationName;
		this.status = "RECEIVED"; this.receivedAt = Instant.now(); touch(actorUserId);
	}
	void markInspected(UUID actorUserId) { this.status = "COMPLETED"; this.inspectedAt = Instant.now(); this.completedBy = actorUserId; touch(actorUserId); }
	void cancel(UUID actorUserId) { this.status = "CANCELLED"; touch(actorUserId); }
	void reverseReceipt(UUID actorUserId) { this.status = "REVERSED"; touch(actorUserId); }
	private void touch(UUID actorUserId) { this.updatedBy = actorUserId; this.updatedAt = Instant.now(); }

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getReturnNumber() { return returnNumber; }
	UUID getSalesOrderId() { return salesOrderId; }
	String getOrderNumber() { return orderNumber; }
	UUID getCustomerId() { return customerId; }
	String getCustomerCode() { return customerCode; }
	String getCustomerName() { return customerName; }
	LocalDate getReturnDate() { return returnDate; }
	String getStatus() { return status; }
	String getReason() { return reason; }
	String getNote() { return note; }
	UUID getWarehouseId() { return warehouseId; }
	String getWarehouseCode() { return warehouseCode; }
	String getWarehouseName() { return warehouseName; }
	UUID getLocationId() { return locationId; }
	String getLocationCode() { return locationCode; }
	String getLocationName() { return locationName; }
	BigDecimal getTotalReturnQuantity() { return totalReturnQuantity; }
	Instant getReceivedAt() { return receivedAt; }
	Instant getInspectedAt() { return inspectedAt; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
	List<SalesReturnLineEntity> getLines() { return lines; }
}

@Entity
@Table(schema = "sales", name = "return_lines")
class SalesReturnLineEntity {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY) @jakarta.persistence.JoinColumn(name = "return_id") private SalesReturnEntity salesReturn;
	@Column(name = "order_line_id") private UUID orderLineId;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "authorized_quantity") private BigDecimal authorizedQuantity;
	@Column(name = "received_quantity") private BigDecimal receivedQuantity = zero();
	@Column(name = "accepted_quantity") private BigDecimal acceptedQuantity = zero();
	@Column(name = "rejected_quantity") private BigDecimal rejectedQuantity = zero();
	@Column(name = "lot_number") private String lotNumber = "";
	@Column(name = "inspection_balance_id") private UUID inspectionBalanceId;
	@Column(name = "receipt_movement_id") private UUID receiptMovementId;
	@Column(name = "stock_summary") private String stockSummary = "";

	protected SalesReturnLineEntity() { }

	SalesReturnLineEntity(SalesReturnEntity salesReturn, SalesOrderLineEntity orderLine, BigDecimal authorizedQuantity) {
		this.id = UUID.randomUUID(); this.salesReturn = salesReturn; this.orderLineId = orderLine.getId();
		this.lineNumber = orderLine.getLineNumber(); this.materialId = orderLine.getMaterialId();
		this.materialCode = orderLine.getMaterialCode(); this.materialName = orderLine.getMaterialName();
		this.materialSpecification = orderLine.getMaterialSpecification(); this.unit = orderLine.getUnit();
		this.authorizedQuantity = authorizedQuantity.setScale(4, RoundingMode.HALF_UP);
	}

	void markReceived(UUID balanceId, UUID movementId, String lotNumber, String summary) {
		this.receivedQuantity = authorizedQuantity; this.inspectionBalanceId = balanceId; this.receiptMovementId = movementId;
		this.lotNumber = lotNumber == null ? "" : lotNumber.trim(); this.stockSummary = summary;
	}
	void markInspected(BigDecimal accepted, BigDecimal rejected, String summary) {
		this.acceptedQuantity = accepted.setScale(4, RoundingMode.HALF_UP);
		this.rejectedQuantity = rejected.setScale(4, RoundingMode.HALF_UP);
		this.stockSummary = summary;
	}

	private static BigDecimal zero() { return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP); }
	UUID getId() { return id; }
	UUID getOrderLineId() { return orderLineId; }
	int getLineNumber() { return lineNumber; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getAuthorizedQuantity() { return authorizedQuantity; }
	BigDecimal getReceivedQuantity() { return receivedQuantity; }
	BigDecimal getAcceptedQuantity() { return acceptedQuantity; }
	BigDecimal getRejectedQuantity() { return rejectedQuantity; }
	String getLotNumber() { return lotNumber; }
	UUID getInspectionBalanceId() { return inspectionBalanceId; }
	UUID getReceiptMovementId() { return receiptMovementId; }
	String getStockSummary() { return stockSummary; }
}

@Entity
@Table(schema = "sales", name = "return_events")
class SalesReturnEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "return_id") private UUID returnId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected SalesReturnEventEntity() { }
	SalesReturnEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID returnId,
			String action, String fromStatus, String toStatus, String reason, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantOrganizationId; this.workspaceId = workspaceId;
		this.actorUserId = actorUserId; this.returnId = returnId; this.action = action; this.fromStatus = fromStatus;
		this.toStatus = toStatus; this.reason = reason.trim(); this.requestId = requestId; this.details = details;
		this.occurredAt = Instant.now();
	}
	UUID getId() { return id; }
	UUID getReturnId() { return returnId; }
	String getAction() { return action; }
	String getFromStatus() { return fromStatus; }
	String getToStatus() { return toStatus; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	Instant getOccurredAt() { return occurredAt; }
}
