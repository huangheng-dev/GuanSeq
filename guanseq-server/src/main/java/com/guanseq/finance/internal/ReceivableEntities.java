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

import com.guanseq.sales.api.SalesReceivableQueryProvider.ReceivableLine;
import com.guanseq.sales.api.SalesReceivableQueryProvider.ReceivableOrder;

@Entity
@Table(schema = "finance", name = "receivable_invoices")
class ReceivableInvoiceEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "invoice_number") private String invoiceNumber;
	@Column(name = "sales_order_id") private UUID salesOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "customer_code") private String customerCode;
	@Column(name = "customer_name") private String customerName;
	private String currency;
	@Column(name = "invoice_date") private LocalDate invoiceDate;
	@Column(name = "due_date") private LocalDate dueDate;
	@Column(name = "tax_rate") private BigDecimal taxRate;
	@Column(name = "net_amount") private BigDecimal netAmount;
	@Column(name = "tax_amount") private BigDecimal taxAmount;
	@Column(name = "gross_amount") private BigDecimal grossAmount;
	@Column(name = "received_amount") private BigDecimal receivedAmount;
	private String status;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<ReceivableInvoiceLineEntity> lines = new ArrayList<>();
	@OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<ReceivableReceiptEntity> receipts = new ArrayList<>();

	protected ReceivableInvoiceEntity() { }

	ReceivableInvoiceEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, String invoiceNumber,
			ReceivableOrder order, LocalDate invoiceDate, LocalDate dueDate, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.invoiceNumber = invoiceNumber;
		this.salesOrderId = order.id();
		this.orderNumber = order.orderNumber();
		this.customerId = order.customerId();
		this.customerCode = order.customerCode();
		this.customerName = order.customerName();
		this.currency = order.currency();
		this.invoiceDate = invoiceDate;
		this.dueDate = dueDate;
		this.taxRate = order.taxRate();
		this.netAmount = money(BigDecimal.ZERO);
		this.taxAmount = money(BigDecimal.ZERO);
		this.grossAmount = money(BigDecimal.ZERO);
		this.receivedAmount = money(BigDecimal.ZERO);
		this.status = "OPEN";
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void addLine(ReceivableLine source, BigDecimal quantity) {
		ReceivableInvoiceLineEntity line = new ReceivableInvoiceLineEntity(this, source, quantity, taxRate);
		lines.add(line);
		netAmount = money(netAmount.add(line.getNetAmount()));
		taxAmount = money(taxAmount.add(line.getTaxAmount()));
		grossAmount = money(netAmount.add(taxAmount));
	}

	ReceivableReceiptEntity applyReceipt(String receiptNumber, BigDecimal amount, LocalDate receiptDate,
			String paymentMethod, String bankReference, String note, String requestId, UUID actorUserId) {
		ReceivableReceiptEntity receipt = new ReceivableReceiptEntity(this, receiptNumber, amount, receiptDate,
				paymentMethod, bankReference, note, requestId, actorUserId);
		receipts.add(receipt);
		receivedAmount = money(receivedAmount.add(amount));
		status = receivedAmount.compareTo(grossAmount) >= 0 ? "PAID" : "PARTIALLY_PAID";
		updatedBy = actorUserId;
		updatedAt = Instant.now();
		return receipt;
	}

	BigDecimal outstandingAmount() { return money(grossAmount.subtract(receivedAmount)); }
	private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getInvoiceNumber() { return invoiceNumber; }
	UUID getSalesOrderId() { return salesOrderId; }
	String getOrderNumber() { return orderNumber; }
	UUID getCustomerId() { return customerId; }
	String getCustomerCode() { return customerCode; }
	String getCustomerName() { return customerName; }
	String getCurrency() { return currency; }
	LocalDate getInvoiceDate() { return invoiceDate; }
	LocalDate getDueDate() { return dueDate; }
	BigDecimal getTaxRate() { return taxRate; }
	BigDecimal getNetAmount() { return netAmount; }
	BigDecimal getTaxAmount() { return taxAmount; }
	BigDecimal getGrossAmount() { return grossAmount; }
	BigDecimal getReceivedAmount() { return receivedAmount; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	List<ReceivableInvoiceLineEntity> getLines() { return lines; }
	List<ReceivableReceiptEntity> getReceipts() { return receipts; }
}

@Entity
@Table(schema = "finance", name = "receivable_invoice_lines")
class ReceivableInvoiceLineEntity {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id") private ReceivableInvoiceEntity invoice;
	@Column(name = "sales_order_line_id") private UUID salesOrderLineId;
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

	protected ReceivableInvoiceLineEntity() { }

	ReceivableInvoiceLineEntity(ReceivableInvoiceEntity invoice, ReceivableLine source, BigDecimal quantity, BigDecimal taxRate) {
		this.id = UUID.randomUUID();
		this.invoice = invoice;
		this.salesOrderLineId = source.id();
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
	UUID getSalesOrderLineId() { return salesOrderLineId; }
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
@Table(schema = "finance", name = "receivable_receipts")
class ReceivableReceiptEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "receipt_number") private String receiptNumber;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invoice_id") private ReceivableInvoiceEntity invoice;
	@Column(name = "invoice_number") private String invoiceNumber;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "customer_code") private String customerCode;
	@Column(name = "customer_name") private String customerName;
	private String currency;
	private BigDecimal amount;
	@Column(name = "receipt_date") private LocalDate receiptDate;
	@Column(name = "payment_method") private String paymentMethod;
	@Column(name = "bank_reference") private String bankReference;
	private String note;
	private String status;
	@Column(name = "request_id") private String requestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;

	protected ReceivableReceiptEntity() { }

	ReceivableReceiptEntity(ReceivableInvoiceEntity invoice, String receiptNumber, BigDecimal amount, LocalDate receiptDate,
			String paymentMethod, String bankReference, String note, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = invoice.getTenantOrganizationId();
		this.owningOrganizationId = invoice.getOwningOrganizationId();
		this.workspaceId = invoice.getWorkspaceId();
		this.receiptNumber = receiptNumber;
		this.invoice = invoice;
		this.invoiceNumber = invoice.getInvoiceNumber();
		this.customerId = invoice.getCustomerId();
		this.customerCode = invoice.getCustomerCode();
		this.customerName = invoice.getCustomerName();
		this.currency = invoice.getCurrency();
		this.amount = amount.setScale(2, RoundingMode.HALF_UP);
		this.receiptDate = receiptDate;
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
	String getReceiptNumber() { return receiptNumber; }
	UUID getInvoiceId() { return invoice.getId(); }
	BigDecimal getAmount() { return amount; }
	LocalDate getReceiptDate() { return receiptDate; }
	String getPaymentMethod() { return paymentMethod; }
	String getBankReference() { return bankReference; }
	String getNote() { return note; }
	String getStatus() { return status; }
	Instant getCreatedAt() { return createdAt; }
}

@Entity
@Table(schema = "finance", name = "receivable_events")
class ReceivableEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "invoice_id") private UUID invoiceId;
	@Column(name = "receipt_id") private UUID receiptId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected ReceivableEventEntity() { }

	ReceivableEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID invoiceId, UUID receiptId,
			String action, String fromStatus, String toStatus, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.invoiceId = invoiceId;
		this.receiptId = receiptId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}
}
