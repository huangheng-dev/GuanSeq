package com.guanseq.finance.internal;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.guanseq.procurement.api.ProcurementPayableQueryProvider.PayableLine;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider.PayableOrder;

@Entity
@Table(schema = "finance", name = "payable_invoices")
class PayableInvoiceEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "invoice_number") private String invoiceNumber;
	@Column(name = "supplier_invoice_number") private String supplierInvoiceNumber;
	@Column(name = "purchase_order_id") private UUID purchaseOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "supplier_id") private UUID supplierId;
	@Column(name = "supplier_code") private String supplierCode;
	@Column(name = "supplier_name") private String supplierName;
	private String currency;
	@Column(name = "invoice_date") private LocalDate invoiceDate;
	@Column(name = "due_date") private LocalDate dueDate;
	@Column(name = "tax_rate") private BigDecimal taxRate;
	@Column(name = "net_amount") private BigDecimal netAmount;
	@Column(name = "tax_amount") private BigDecimal taxAmount;
	@Column(name = "gross_amount") private BigDecimal grossAmount;
	@Column(name = "paid_amount") private BigDecimal paidAmount;
	private String status;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<PayableInvoiceLineEntity> lines = new ArrayList<>();
	@OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<PayablePaymentEntity> payments = new ArrayList<>();

	protected PayableInvoiceEntity() { }

	PayableInvoiceEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, String invoiceNumber,
			String supplierInvoiceNumber, PayableOrder order, LocalDate invoiceDate, LocalDate dueDate,
			String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.invoiceNumber = invoiceNumber;
		this.supplierInvoiceNumber = supplierInvoiceNumber.trim();
		this.purchaseOrderId = order.id();
		this.orderNumber = order.orderNumber();
		this.supplierId = order.supplierId();
		this.supplierCode = order.supplierCode();
		this.supplierName = order.supplierName();
		this.currency = order.currency();
		this.invoiceDate = invoiceDate;
		this.dueDate = dueDate;
		this.taxRate = order.taxRate();
		this.netAmount = money(BigDecimal.ZERO);
		this.taxAmount = money(BigDecimal.ZERO);
		this.grossAmount = money(BigDecimal.ZERO);
		this.paidAmount = money(BigDecimal.ZERO);
		this.status = "OPEN";
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void addLine(PayableLine source, BigDecimal quantity) {
		PayableInvoiceLineEntity line = new PayableInvoiceLineEntity(this, source, quantity, taxRate);
		lines.add(line);
		netAmount = money(netAmount.add(line.getNetAmount()));
		taxAmount = money(taxAmount.add(line.getTaxAmount()));
		grossAmount = money(netAmount.add(taxAmount));
	}

	PayablePaymentEntity applyPayment(String paymentNumber, BigDecimal amount, LocalDate paymentDate,
			String paymentMethod, String bankReference, String note, String requestId, UUID actorUserId) {
		PayablePaymentEntity payment = new PayablePaymentEntity(this, paymentNumber, amount, paymentDate,
				paymentMethod, bankReference, note, requestId, actorUserId);
		payments.add(payment);
		paidAmount = money(paidAmount.add(amount));
		status = paidAmount.compareTo(grossAmount) >= 0 ? "PAID" : "PARTIALLY_PAID";
		updatedBy = actorUserId;
		updatedAt = Instant.now();
		return payment;
	}

	BigDecimal outstandingAmount() { return money(grossAmount.subtract(paidAmount)); }
	private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getInvoiceNumber() { return invoiceNumber; }
	String getSupplierInvoiceNumber() { return supplierInvoiceNumber; }
	UUID getPurchaseOrderId() { return purchaseOrderId; }
	String getOrderNumber() { return orderNumber; }
	UUID getSupplierId() { return supplierId; }
	String getSupplierCode() { return supplierCode; }
	String getSupplierName() { return supplierName; }
	String getCurrency() { return currency; }
	LocalDate getInvoiceDate() { return invoiceDate; }
	LocalDate getDueDate() { return dueDate; }
	BigDecimal getTaxRate() { return taxRate; }
	BigDecimal getNetAmount() { return netAmount; }
	BigDecimal getTaxAmount() { return taxAmount; }
	BigDecimal getGrossAmount() { return grossAmount; }
	BigDecimal getPaidAmount() { return paidAmount; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	List<PayableInvoiceLineEntity> getLines() { return lines; }
	List<PayablePaymentEntity> getPayments() { return payments; }
}

@Entity
@Table(schema = "finance", name = "payable_invoice_lines")
class PayableInvoiceLineEntity {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id") private PayableInvoiceEntity invoice;
	@Column(name = "purchase_order_line_id") private UUID purchaseOrderLineId;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "invoice_quantity") private BigDecimal invoiceQuantity;
	@Column(name = "unit_price") private BigDecimal unitPrice;
	@Column(name = "net_amount") private BigDecimal netAmount;
	@Column(name = "tax_amount") private BigDecimal taxAmount;
	@Column(name = "gross_amount") private BigDecimal grossAmount;

	protected PayableInvoiceLineEntity() { }

	PayableInvoiceLineEntity(PayableInvoiceEntity invoice, PayableLine source, BigDecimal quantity, BigDecimal taxRate) {
		this.id = UUID.randomUUID();
		this.invoice = invoice;
		this.purchaseOrderLineId = source.id();
		this.lineNumber = source.lineNumber();
		this.materialId = source.materialId();
		this.materialCode = source.materialCode();
		this.materialName = source.materialName();
		this.materialSpecification = source.materialSpecification();
		this.unit = source.unit();
		this.invoiceQuantity = quantity.setScale(4, RoundingMode.HALF_UP);
		this.unitPrice = source.unitPrice().setScale(6, RoundingMode.HALF_UP);
		this.netAmount = invoiceQuantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
		this.taxAmount = netAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
		this.grossAmount = netAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
	}

	UUID getId() { return id; }
	UUID getPurchaseOrderLineId() { return purchaseOrderLineId; }
	int getLineNumber() { return lineNumber; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getInvoiceQuantity() { return invoiceQuantity; }
	BigDecimal getUnitPrice() { return unitPrice; }
	BigDecimal getNetAmount() { return netAmount; }
	BigDecimal getTaxAmount() { return taxAmount; }
	BigDecimal getGrossAmount() { return grossAmount; }
}

@Entity
@Table(schema = "finance", name = "payable_payments")
class PayablePaymentEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "payment_number") private String paymentNumber;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id") private PayableInvoiceEntity invoice;
	@Column(name = "invoice_number") private String invoiceNumber;
	@Column(name = "supplier_id") private UUID supplierId;
	@Column(name = "supplier_code") private String supplierCode;
	@Column(name = "supplier_name") private String supplierName;
	private String currency;
	private BigDecimal amount;
	@Column(name = "payment_date") private LocalDate paymentDate;
	@Column(name = "payment_method") private String paymentMethod;
	@Column(name = "bank_reference") private String bankReference;
	private String note;
	private String status;
	@Column(name = "request_id") private String requestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;

	protected PayablePaymentEntity() { }

	PayablePaymentEntity(PayableInvoiceEntity invoice, String paymentNumber, BigDecimal amount, LocalDate paymentDate,
			String paymentMethod, String bankReference, String note, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = invoice.getTenantOrganizationId();
		this.owningOrganizationId = invoice.getOwningOrganizationId();
		this.workspaceId = invoice.getWorkspaceId();
		this.paymentNumber = paymentNumber;
		this.invoice = invoice;
		this.invoiceNumber = invoice.getInvoiceNumber();
		this.supplierId = invoice.getSupplierId();
		this.supplierCode = invoice.getSupplierCode();
		this.supplierName = invoice.getSupplierName();
		this.currency = invoice.getCurrency();
		this.amount = amount.setScale(2, RoundingMode.HALF_UP);
		this.paymentDate = paymentDate;
		this.paymentMethod = paymentMethod;
		this.bankReference = trimToNull(bankReference);
		this.note = trimToNull(note);
		this.status = "POSTED";
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
	}

	private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	UUID getId() { return id; }
	String getPaymentNumber() { return paymentNumber; }
	UUID getInvoiceId() { return invoice.getId(); }
	BigDecimal getAmount() { return amount; }
	LocalDate getPaymentDate() { return paymentDate; }
	String getPaymentMethod() { return paymentMethod; }
	String getBankReference() { return bankReference; }
	String getNote() { return note; }
	String getStatus() { return status; }
	Instant getCreatedAt() { return createdAt; }
}

@Entity
@Table(schema = "finance", name = "payable_events")
class PayableEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "invoice_id") private UUID invoiceId;
	@Column(name = "payment_id") private UUID paymentId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected PayableEventEntity() { }

	PayableEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID invoiceId, UUID paymentId,
			String action, String fromStatus, String toStatus, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.invoiceId = invoiceId;
		this.paymentId = paymentId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}
}
