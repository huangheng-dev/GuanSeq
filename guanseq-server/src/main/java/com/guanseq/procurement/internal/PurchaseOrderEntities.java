package com.guanseq.procurement.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "procurement", name = "suppliers")
class SupplierEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	private String code;
	private String name;
	@Column(name = "contact_name") private String contactName;
	@Column(name = "contact_phone") private String contactPhone;
	private String status;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@Version private long version;

	protected SupplierEntity() { }

	SupplierEntity(UUID id, UUID tenantOrganizationId, UUID owningOrganizationId, String code, String name,
			String contactName, String contactPhone, UUID actor) {
		this.id = id;
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.code = code;
		this.name = name;
		this.contactName = contactName;
		this.contactPhone = contactPhone;
		this.status = "ACTIVE";
		this.createdBy = actor;
		this.createdAt = Instant.now();
		this.updatedBy = actor;
		this.updatedAt = this.createdAt;
	}

	void update(String name, String contactName, String contactPhone, UUID actor) {
		this.name = name;
		this.contactName = contactName;
		this.contactPhone = contactPhone;
		this.updatedBy = actor;
		this.updatedAt = Instant.now();
	}

	void toggleStatus(String status, UUID actor) {
		this.status = status;
		this.updatedBy = actor;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	String getCode() { return code; }
	String getName() { return name; }
	String getContactName() { return contactName; }
	String getContactPhone() { return contactPhone; }
	String getStatus() { return status; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
	long getVersion() { return version; }
}

@Entity
@Table(schema = "procurement", name = "purchase_orders")
class PurchaseOrderEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "supplier_id") private UUID supplierId;
	@Column(name = "supplier_code") private String supplierCode;
	@Column(name = "supplier_name") private String supplierName;
	private String currency;
	@Column(name = "tax_rate") private BigDecimal taxRate;
	@Column(name = "requested_receipt_date") private LocalDate requestedReceiptDate;
	@Column(name = "promised_receipt_date") private LocalDate promisedReceiptDate;
	private String buyer;
	private String status;
	@Column(name = "total_net_amount") private BigDecimal totalNetAmount;
	@Column(name = "total_tax_amount") private BigDecimal totalTaxAmount;
	@Column(name = "total_gross_amount") private BigDecimal totalGrossAmount;
	@Column(name = "rejection_reason") private String rejectionReason;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "source_id") private UUID sourceId;
	@Column(name = "source_number") private String sourceNumber;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<PurchaseOrderLineEntity> lines = new ArrayList<>();

	protected PurchaseOrderEntity() { }

	PurchaseOrderEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String orderNumber, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.orderNumber = orderNumber; this.status = "DRAFT"; this.sourceType = "MANUAL";
		this.totalNetAmount = BigDecimal.ZERO.setScale(2); this.totalTaxAmount = BigDecimal.ZERO.setScale(2);
		this.totalGrossAmount = BigDecimal.ZERO.setScale(2); this.createdBy = actorId; this.createdAt = Instant.now();
		this.updatedBy = actorId; this.updatedAt = this.createdAt;
	}

	void updateHeader(SupplierEntity supplier, String currency, BigDecimal taxRate, LocalDate requestedDate,
			LocalDate promisedDate, String buyer, UUID actorId) {
		this.supplierId = supplier.getId(); this.supplierCode = supplier.getCode(); this.supplierName = supplier.getName();
		this.currency = currency; this.taxRate = taxRate; this.requestedReceiptDate = requestedDate;
		this.promisedReceiptDate = promisedDate; this.buyer = buyer.trim(); this.updatedBy = actorId;
		this.updatedAt = Instant.now(); if ("REJECTED".equals(status)) rejectionReason = null;
	}

	void replaceLines(List<PurchaseOrderLineEntity> replacements, UUID actorId) {
		lines.clear(); lines.addAll(replacements);
		totalNetAmount = replacements.stream().map(PurchaseOrderLineEntity::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		totalTaxAmount = replacements.stream().map(PurchaseOrderLineEntity::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		totalGrossAmount = replacements.stream().map(PurchaseOrderLineEntity::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		updatedBy = actorId; updatedAt = Instant.now();
	}

	void transition(String target, String comment, UUID actorId) {
		status = target; rejectionReason = "REJECTED".equals(target) ? comment : null; updatedBy = actorId; updatedAt = Instant.now();
	}

	void attachMrpSource(UUID suggestionId, String sourceNumber) {
		this.sourceType = "MRP"; this.sourceId = suggestionId; this.sourceNumber = sourceNumber;
	}

	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getOrderNumber() { return orderNumber; } UUID getSupplierId() { return supplierId; }
	String getSupplierCode() { return supplierCode; } String getSupplierName() { return supplierName; }
	String getCurrency() { return currency; } BigDecimal getTaxRate() { return taxRate; }
	LocalDate getRequestedReceiptDate() { return requestedReceiptDate; } LocalDate getPromisedReceiptDate() { return promisedReceiptDate; }
	String getBuyer() { return buyer; } String getStatus() { return status; }
	BigDecimal getTotalNetAmount() { return totalNetAmount; } BigDecimal getTotalTaxAmount() { return totalTaxAmount; }
	BigDecimal getTotalGrossAmount() { return totalGrossAmount; } String getRejectionReason() { return rejectionReason; }
	String getSourceType() { return sourceType; } UUID getSourceId() { return sourceId; } String getSourceNumber() { return sourceNumber; }
	long getVersion() { return version; } Instant getUpdatedAt() { return updatedAt; } List<PurchaseOrderLineEntity> getLines() { return lines; }
}

@Entity
@Table(schema = "procurement", name = "purchase_order_lines")
class PurchaseOrderLineEntity {
	@Id private UUID id;
	@jakarta.persistence.ManyToOne(fetch = FetchType.LAZY) @jakarta.persistence.JoinColumn(name = "order_id")
	private PurchaseOrderEntity order;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "ordered_quantity") private BigDecimal orderedQuantity;
	@Column(name = "received_quantity") private BigDecimal receivedQuantity;
	@Column(name = "unit_price") private BigDecimal unitPrice;
	@Column(name = "net_amount") private BigDecimal netAmount;
	@Column(name = "tax_amount") private BigDecimal taxAmount;
	@Column(name = "gross_amount") private BigDecimal grossAmount;

	protected PurchaseOrderLineEntity() { }
	PurchaseOrderLineEntity(PurchaseOrderEntity order, int lineNumber, UUID materialId, String materialCode,
			String materialName, String specification, String unit, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal taxRate) {
		this.id = UUID.randomUUID(); this.order = order; this.lineNumber = lineNumber; this.materialId = materialId;
		this.materialCode = materialCode; this.materialName = materialName; this.materialSpecification = specification;
		this.unit = unit; this.orderedQuantity = quantity; this.receivedQuantity = BigDecimal.ZERO;
		this.unitPrice = unitPrice; this.netAmount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
		this.taxAmount = netAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
		this.grossAmount = netAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
	}
	void applyAcceptedReceipt(BigDecimal acceptedQuantity) {
		BigDecimal next = receivedQuantity.add(acceptedQuantity);
		if (next.compareTo(orderedQuantity) > 0) throw new IllegalStateException("采购订单行累计合格入库数量不能超过订单数量");
		receivedQuantity = next;
	}
	UUID getId() { return id; } int getLineNumber() { return lineNumber; } UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; } String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; } String getUnit() { return unit; }
	BigDecimal getOrderedQuantity() { return orderedQuantity; } BigDecimal getReceivedQuantity() { return receivedQuantity; }
	BigDecimal getOutstandingQuantity() { return orderedQuantity.subtract(receivedQuantity); }
	BigDecimal getUnitPrice() { return unitPrice; } BigDecimal getNetAmount() { return netAmount; }
	BigDecimal getTaxAmount() { return taxAmount; } BigDecimal getGrossAmount() { return grossAmount; }
}

@Entity
@Table(schema = "procurement", name = "purchase_order_events")
class PurchaseOrderEventEntity {
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
	protected PurchaseOrderEventEntity() { }
	PurchaseOrderEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID orderId, String action,
			String from, String to, String requestId, String comment, Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.orderId = orderId; this.action = action; this.fromStatus = from;
		this.toStatus = to; this.requestId = requestId; this.comment = comment; this.details = details; this.occurredAt = Instant.now();
	}
}
