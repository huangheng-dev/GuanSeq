package com.guanseq.procurement.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "procurement", name = "purchase_receipts")
class PurchaseReceiptEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "receipt_number") private String receiptNumber;
	@Column(name = "purchase_order_id") private UUID purchaseOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "supplier_id") private UUID supplierId;
	@Column(name = "supplier_code") private String supplierCode;
	@Column(name = "supplier_name") private String supplierName;
	@Column(name = "warehouse_id") private UUID warehouseId;
	@Column(name = "warehouse_code") private String warehouseCode;
	@Column(name = "warehouse_name") private String warehouseName;
	@Column(name = "location_id") private UUID locationId;
	@Column(name = "location_code") private String locationCode;
	@Column(name = "location_name") private String locationName;
	private String note;
	private String source;
	private String status;
	@Column(name = "total_received_quantity") private BigDecimal totalReceivedQuantity;
	@Column(name = "accepted_quantity") private BigDecimal acceptedQuantity;
	@Column(name = "rejected_quantity") private BigDecimal rejectedQuantity;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<PurchaseReceiptLineEntity> lines = new ArrayList<>();

	protected PurchaseReceiptEntity() { }
	PurchaseReceiptEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String number,
			PurchaseOrderEntity order, WarehouseSnapshot warehouse, StorageLocationSnapshot location, String note,
			String source, String requestId, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.receiptNumber = number; this.purchaseOrderId = order.getId();
		this.orderNumber = order.getOrderNumber(); this.supplierId = order.getSupplierId(); this.supplierCode = order.getSupplierCode();
		this.supplierName = order.getSupplierName(); this.warehouseId = warehouse.id(); this.warehouseCode = warehouse.code();
		this.warehouseName = warehouse.name(); this.locationId = location.id(); this.locationCode = location.code();
		this.locationName = location.name(); this.note = note; this.source = source; this.totalReceivedQuantity = BigDecimal.ZERO;
		this.acceptedQuantity = BigDecimal.ZERO; this.rejectedQuantity = BigDecimal.ZERO; this.requestId = requestId;
		this.createdBy = actorId; this.createdAt = Instant.now(); this.updatedBy = actorId; this.updatedAt = this.createdAt;
	}

	PurchaseReceiptLineEntity addLine(PurchaseOrderLineEntity orderLine, BigDecimal receivedQuantity, boolean inspectionRequired,
			String lotNumber, String requestId, UUID actorId) {
		PurchaseReceiptLineEntity line = new PurchaseReceiptLineEntity(this, lines.size() + 1, orderLine,
				receivedQuantity, inspectionRequired, lotNumber, requestId, actorId);
		lines.add(line); totalReceivedQuantity = totalReceivedQuantity.add(receivedQuantity);
		if (!inspectionRequired) acceptedQuantity = acceptedQuantity.add(receivedQuantity);
		updateStatus(); updatedBy = actorId; updatedAt = Instant.now(); return line;
	}

	void settleLine(PurchaseReceiptLineEntity line, BigDecimal accepted, BigDecimal rejected, UUID actorId) {
		line.completeInspection(accepted, rejected);
		acceptedQuantity = acceptedQuantity.add(accepted); rejectedQuantity = rejectedQuantity.add(rejected);
		updateStatus(); updatedBy = actorId; updatedAt = Instant.now();
	}

	private void updateStatus() {
		boolean pending = lines.stream().anyMatch(line -> "PENDING_INSPECTION".equals(line.getStatus()));
		if (pending) { status = "PENDING_INSPECTION"; return; }
		BigDecimal accepted = lines.stream().map(PurchaseReceiptLineEntity::getAcceptedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal rejected = lines.stream().map(PurchaseReceiptLineEntity::getRejectedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (rejected.signum() == 0) status = "RECEIVED";
		else if (accepted.signum() == 0) status = "REJECTED_CLOSED";
		else status = "PARTIALLY_RECEIVED";
	}

	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; } UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; } String getReceiptNumber() { return receiptNumber; }
	UUID getPurchaseOrderId() { return purchaseOrderId; } String getOrderNumber() { return orderNumber; }
	UUID getSupplierId() { return supplierId; } String getSupplierCode() { return supplierCode; } String getSupplierName() { return supplierName; }
	UUID getWarehouseId() { return warehouseId; } String getWarehouseCode() { return warehouseCode; } String getWarehouseName() { return warehouseName; }
	UUID getLocationId() { return locationId; } String getLocationCode() { return locationCode; } String getLocationName() { return locationName; }
	String getNote() { return note; } String getSource() { return source; } String getStatus() { return status; } BigDecimal getTotalReceivedQuantity() { return totalReceivedQuantity; }
	BigDecimal getAcceptedQuantity() { return acceptedQuantity; } BigDecimal getRejectedQuantity() { return rejectedQuantity; }
	String getRequestId() { return requestId; } long getVersion() { return version; } Instant getCreatedAt() { return createdAt; }
	List<PurchaseReceiptLineEntity> getLines() { return lines; }

	record WarehouseSnapshot(UUID id, String code, String name) { }
	record StorageLocationSnapshot(UUID id, String code, String name) { }
}

@Entity
@Table(schema = "procurement", name = "purchase_receipt_lines")
class PurchaseReceiptLineEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@jakarta.persistence.ManyToOne(fetch = FetchType.LAZY) @jakarta.persistence.JoinColumn(name = "receipt_id")
	private PurchaseReceiptEntity receipt;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "purchase_order_line_id") private UUID purchaseOrderLineId;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "received_quantity") private BigDecimal receivedQuantity;
	@Column(name = "inspection_required") private boolean inspectionRequired;
	@Column(name = "lot_number") private String lotNumber;
	private String status;
	@Column(name = "inspection_id") private UUID inspectionId;
	@Column(name = "accepted_quantity") private BigDecimal acceptedQuantity;
	@Column(name = "rejected_quantity") private BigDecimal rejectedQuantity;
	@Column(name = "inspection_balance_id") private UUID inspectionBalanceId;
	@Column(name = "inspection_movement_id") private UUID inspectionMovementId;
	@Column(name = "accepted_balance_id") private UUID acceptedBalanceId;
	@Column(name = "accepted_movement_id") private UUID acceptedMovementId;
	@Column(name = "rejected_balance_id") private UUID rejectedBalanceId;
	@Column(name = "rejected_movement_id") private UUID rejectedMovementId;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected PurchaseReceiptLineEntity() { }
	PurchaseReceiptLineEntity(PurchaseReceiptEntity receipt, int lineNumber, PurchaseOrderLineEntity orderLine,
			BigDecimal receivedQuantity, boolean inspectionRequired, String lotNumber, String requestId, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = receipt.getTenantOrganizationId(); this.receipt = receipt;
		this.lineNumber = lineNumber; this.purchaseOrderLineId = orderLine.getId(); this.materialId = orderLine.getMaterialId();
		this.materialCode = orderLine.getMaterialCode(); this.materialName = orderLine.getMaterialName();
		this.materialSpecification = orderLine.getMaterialSpecification(); this.unit = orderLine.getUnit();
		this.receivedQuantity = receivedQuantity; this.inspectionRequired = inspectionRequired;
		this.lotNumber = lotNumber == null ? "" : lotNumber.trim(); this.status = inspectionRequired ? "PENDING_INSPECTION" : "RECEIVED";
		this.requestId = requestId;
		this.createdBy = actorId; this.createdAt = Instant.now(); this.updatedBy = actorId; this.updatedAt = this.createdAt;
	}

	void attachInspection(UUID inspectionId, UUID balanceId, UUID movementId) {
		this.inspectionId = inspectionId; this.inspectionBalanceId = balanceId; this.inspectionMovementId = movementId;
	}
	void markDirectReceived(UUID balanceId, UUID movementId) {
		this.acceptedQuantity = receivedQuantity; this.rejectedQuantity = BigDecimal.ZERO;
		this.acceptedBalanceId = balanceId; this.acceptedMovementId = movementId;
	}
	void completeInspection(BigDecimal accepted, BigDecimal rejected) {
		if (!"PENDING_INSPECTION".equals(status)) throw new IllegalStateException("只有待检收货行可以登记 IQC 结果");
		if (accepted.add(rejected).compareTo(receivedQuantity) != 0) throw new IllegalStateException("IQC 合格与不合格数量之和必须等于收货数量");
		this.acceptedQuantity = accepted; this.rejectedQuantity = rejected;
		this.status = rejected.signum() == 0 ? "RECEIVED" : accepted.signum() == 0 ? "REJECTED_CLOSED" : "PARTIALLY_RECEIVED";
		this.updatedAt = Instant.now();
	}
	void attachAcceptedStock(UUID balanceId, UUID movementId) { this.acceptedBalanceId = balanceId; this.acceptedMovementId = movementId; }
	void attachRejectedStock(UUID balanceId, UUID movementId) { this.rejectedBalanceId = balanceId; this.rejectedMovementId = movementId; }

	PurchaseReceiptEntity getReceipt() { return receipt; }
	UUID getId() { return id; } int getLineNumber() { return lineNumber; } UUID getPurchaseOrderLineId() { return purchaseOrderLineId; }
	UUID getMaterialId() { return materialId; } String getMaterialCode() { return materialCode; } String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; } String getUnit() { return unit; }
	BigDecimal getReceivedQuantity() { return receivedQuantity; } boolean isInspectionRequired() { return inspectionRequired; }
	String getLotNumber() { return lotNumber; } String getStatus() { return status; } UUID getInspectionId() { return inspectionId; }
	BigDecimal getAcceptedQuantity() { return acceptedQuantity == null ? BigDecimal.ZERO : acceptedQuantity; }
	BigDecimal getRejectedQuantity() { return rejectedQuantity == null ? BigDecimal.ZERO : rejectedQuantity; }
	UUID getInspectionBalanceId() { return inspectionBalanceId; } UUID getAcceptedBalanceId() { return acceptedBalanceId; }
	UUID getRejectedBalanceId() { return rejectedBalanceId; } long getVersion() { return version; } Instant getCreatedAt() { return createdAt; }
	String stockSummary() {
		return inspectionRequired ? switch (status) {
			case "PENDING_INSPECTION" -> "待检库存 " + receivedQuantity.stripTrailingZeros().toPlainString();
			case "RECEIVED" -> "合格入库 " + acceptedQuantity.stripTrailingZeros().toPlainString();
			case "REJECTED_CLOSED" -> "不合格隔离 " + rejectedQuantity.stripTrailingZeros().toPlainString();
			default -> "合格 " + acceptedQuantity.stripTrailingZeros().toPlainString() + " / 不合格 " + rejectedQuantity.stripTrailingZeros().toPlainString();
		} : "免检入库 " + receivedQuantity.stripTrailingZeros().toPlainString();
	}
}

@Entity
@Table(schema = "procurement", name = "purchase_receipt_events")
class PurchaseReceiptEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "receipt_id") private UUID receiptId;
	@Column(name = "receipt_line_id") private UUID receiptLineId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	private String comment;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;
	protected PurchaseReceiptEventEntity() { }
	PurchaseReceiptEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID receiptId, UUID receiptLineId,
			String action, String from, String to, String requestId, String comment, Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId; this.actorUserId = actorId;
		this.receiptId = receiptId; this.receiptLineId = receiptLineId; this.action = action; this.fromStatus = from; this.toStatus = to;
		this.requestId = requestId; this.comment = comment; this.details = details; this.occurredAt = Instant.now();
	}
}
