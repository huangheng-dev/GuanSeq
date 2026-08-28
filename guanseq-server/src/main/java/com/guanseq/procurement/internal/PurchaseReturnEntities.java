package com.guanseq.procurement.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "procurement", name = "purchase_returns")
class PurchaseReturnEntity {
	@Id private UUID id;
	@Column(name="tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name="owning_organization_id") private UUID owningOrganizationId;
	@Column(name="workspace_id") private UUID workspaceId;
	@Column(name="return_number") private String returnNumber;
	@Column(name="purchase_order_id") private UUID purchaseOrderId;
	@Column(name="order_number") private String orderNumber;
	@Column(name="supplier_id") private UUID supplierId;
	@Column(name="supplier_code") private String supplierCode;
	@Column(name="supplier_name") private String supplierName;
	@Column(name="return_date") private LocalDate returnDate;
	private String reason;
	private String note;
	private String status;
	@Column(name="total_return_quantity") private BigDecimal totalReturnQuantity;
	@Column(name="accepted_return_quantity") private BigDecimal acceptedReturnQuantity;
	@Column(name="blocked_return_quantity") private BigDecimal blockedReturnQuantity;
	@Column(name="request_id") private String requestId;
	@Version private long version;
	@Column(name="created_by") private UUID createdBy;
	@Column(name="created_at") private Instant createdAt;
	@Column(name="updated_by") private UUID updatedBy;
	@Column(name="updated_at") private Instant updatedAt;
	@OneToMany(mappedBy="purchaseReturn", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.LAZY)
	private List<PurchaseReturnLineEntity> lines = new ArrayList<>();
	protected PurchaseReturnEntity() { }
	PurchaseReturnEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String number, PurchaseOrderEntity order,
			LocalDate date, String reason, String note, String requestId, UUID actorId) {
		this.id=UUID.randomUUID(); this.tenantOrganizationId=tenantId; this.owningOrganizationId=organizationId;
		this.workspaceId=workspaceId; this.returnNumber=number; this.purchaseOrderId=order.getId(); this.orderNumber=order.getOrderNumber();
		this.supplierId=order.getSupplierId(); this.supplierCode=order.getSupplierCode(); this.supplierName=order.getSupplierName();
		this.returnDate=date; this.reason=reason; this.note=note; this.status="PENDING_SHIPMENT"; this.requestId=requestId;
		this.totalReturnQuantity=BigDecimal.ZERO; this.acceptedReturnQuantity=BigDecimal.ZERO; this.blockedReturnQuantity=BigDecimal.ZERO;
		this.createdBy=actorId; this.createdAt=Instant.now(); this.updatedBy=actorId; this.updatedAt=this.createdAt;
	}
	PurchaseReturnLineEntity addLine(PurchaseReceiptLineEntity source, String qualityStatus, UUID balanceId,
			BigDecimal quantity) {
		PurchaseReturnLineEntity line=new PurchaseReturnLineEntity(this, lines.size()+1, source, qualityStatus, balanceId, quantity);
		lines.add(line); totalReturnQuantity=totalReturnQuantity.add(quantity);
		if ("AVAILABLE".equals(qualityStatus)) acceptedReturnQuantity=acceptedReturnQuantity.add(quantity);
		else blockedReturnQuantity=blockedReturnQuantity.add(quantity);
		return line;
	}
	void transition(String target, UUID actorId) { status=target; updatedBy=actorId; updatedAt=Instant.now(); }
	UUID getId(){return id;} UUID getTenantOrganizationId(){return tenantOrganizationId;} UUID getOwningOrganizationId(){return owningOrganizationId;}
	UUID getWorkspaceId(){return workspaceId;} String getReturnNumber(){return returnNumber;} UUID getPurchaseOrderId(){return purchaseOrderId;}
	String getOrderNumber(){return orderNumber;} UUID getSupplierId(){return supplierId;} String getSupplierCode(){return supplierCode;}
	String getSupplierName(){return supplierName;} LocalDate getReturnDate(){return returnDate;} String getReason(){return reason;}
	String getNote(){return note;} String getStatus(){return status;} BigDecimal getTotalReturnQuantity(){return totalReturnQuantity;}
	BigDecimal getAcceptedReturnQuantity(){return acceptedReturnQuantity;} BigDecimal getBlockedReturnQuantity(){return blockedReturnQuantity;}
	long getVersion(){return version;} Instant getCreatedAt(){return createdAt;} Instant getUpdatedAt(){return updatedAt;}
	List<PurchaseReturnLineEntity> getLines(){return lines;}
}

@Entity
@Table(schema="procurement", name="purchase_return_lines")
class PurchaseReturnLineEntity {
	@Id private UUID id;
	@Column(name="tenant_organization_id") private UUID tenantOrganizationId;
	@ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="return_id") private PurchaseReturnEntity purchaseReturn;
	@Column(name="line_number") private int lineNumber;
	@Column(name="purchase_receipt_line_id") private UUID purchaseReceiptLineId;
	@Column(name="purchase_order_line_id") private UUID purchaseOrderLineId;
	@Column(name="material_id") private UUID materialId;
	@Column(name="material_code") private String materialCode;
	@Column(name="material_name") private String materialName;
	@Column(name="material_specification") private String materialSpecification;
	private String unit;
	@Column(name="quality_status") private String qualityStatus;
	@Column(name="authorized_quantity") private BigDecimal authorizedQuantity;
	@Column(name="shipped_quantity") private BigDecimal shippedQuantity;
	@Column(name="stock_balance_id") private UUID stockBalanceId;
	@Column(name="stock_movement_id") private UUID stockMovementId;
	@Column(name="warehouse_code") private String warehouseCode;
	@Column(name="location_code") private String locationCode;
	@Column(name="lot_number") private String lotNumber;
	@Version private long version;
	protected PurchaseReturnLineEntity(){ }
	PurchaseReturnLineEntity(PurchaseReturnEntity parent,int lineNumber,PurchaseReceiptLineEntity source,String qualityStatus,UUID balanceId,BigDecimal quantity){
		this.id=UUID.randomUUID(); this.tenantOrganizationId=parent.getTenantOrganizationId(); this.purchaseReturn=parent; this.lineNumber=lineNumber;
		this.purchaseReceiptLineId=source.getId(); this.purchaseOrderLineId=source.getPurchaseOrderLineId(); this.materialId=source.getMaterialId();
		this.materialCode=source.getMaterialCode(); this.materialName=source.getMaterialName(); this.materialSpecification=source.getMaterialSpecification();
		this.unit=source.getUnit(); this.qualityStatus=qualityStatus; this.authorizedQuantity=quantity; this.shippedQuantity=BigDecimal.ZERO;
		this.stockBalanceId=balanceId; this.lotNumber=source.getLotNumber();
	}
	void markShipped(UUID movementId,String warehouse,String location,String lot){this.shippedQuantity=authorizedQuantity;this.stockMovementId=movementId;this.warehouseCode=warehouse;this.locationCode=location;this.lotNumber=lot;}
	void clearShipment(){this.shippedQuantity=BigDecimal.ZERO;}
	UUID getId(){return id;} UUID getPurchaseReceiptLineId(){return purchaseReceiptLineId;} UUID getPurchaseOrderLineId(){return purchaseOrderLineId;}
	int getLineNumber(){return lineNumber;} UUID getMaterialId(){return materialId;} String getMaterialCode(){return materialCode;}
	String getMaterialName(){return materialName;} String getMaterialSpecification(){return materialSpecification;} String getUnit(){return unit;}
	String getQualityStatus(){return qualityStatus;} BigDecimal getAuthorizedQuantity(){return authorizedQuantity;} BigDecimal getShippedQuantity(){return shippedQuantity;}
	UUID getStockBalanceId(){return stockBalanceId;} UUID getStockMovementId(){return stockMovementId;} String getWarehouseCode(){return warehouseCode;}
	String getLocationCode(){return locationCode;} String getLotNumber(){return lotNumber;}
}

@Entity
@Table(schema="procurement",name="purchase_return_events")
class PurchaseReturnEventEntity {
	@Id private UUID id;
	@Column(name="tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name="workspace_id") private UUID workspaceId;
	@Column(name="actor_user_id") private UUID actorUserId;
	@Column(name="return_id") private UUID returnId;
	private String action;
	@Column(name="from_status") private String fromStatus;
	@Column(name="to_status") private String toStatus;
	private String reason;
	@Column(name="request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String,Object> details;
	@Column(name="occurred_at") private Instant occurredAt;
	protected PurchaseReturnEventEntity(){ }
	PurchaseReturnEventEntity(UUID tenantId,UUID workspaceId,UUID actorId,UUID returnId,String action,String from,String to,String reason,String requestId,Map<String,Object> details){
		this.id=UUID.randomUUID();this.tenantOrganizationId=tenantId;this.workspaceId=workspaceId;this.actorUserId=actorId;this.returnId=returnId;
		this.action=action;this.fromStatus=from;this.toStatus=to;this.reason=reason;this.requestId=requestId;this.details=details;this.occurredAt=Instant.now();
	}
	UUID getId(){return id;} UUID getReturnId(){return returnId;} String getAction(){return action;} String getFromStatus(){return fromStatus;} String getToStatus(){return toStatus;}
	String getReason(){return reason;} String getRequestId(){return requestId;} Instant getOccurredAt(){return occurredAt;}
}
