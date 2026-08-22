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
	@Column(name = "credit_balance") private BigDecimal creditBalance;
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
		this.creditBalance = money(BigDecimal.ZERO);
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
		ReceivableReceiptEntity receipt = new ReceivableReceiptEntity(this, receiptNumber, "RECEIPT", amount, receiptDate,
				paymentMethod, bankReference, note, requestId, actorUserId);
		receipts.add(receipt);
		receivedAmount = money(receivedAmount.add(amount));
		recalculateStatus(actorUserId);
		return receipt;
	}

	ReceivableReceiptEntity applyRefund(String refundNumber, BigDecimal amount, LocalDate refundDate,
			String paymentMethod, String bankReference, String note, String requestId, UUID actorUserId) {
		ReceivableReceiptEntity refund = new ReceivableReceiptEntity(this, refundNumber, "REFUND", amount, refundDate,
				paymentMethod, bankReference, note, requestId, actorUserId);
		receipts.add(refund);
		creditBalance = money(creditBalance.subtract(amount));
		recalculateStatus(actorUserId);
		return refund;
	}

	void applyCreditNote(BigDecimal creditGrossAmount, UUID actorUserId) {
		creditBalance = money(creditBalance.add(creditGrossAmount.abs()));
		recalculateStatus(actorUserId);
	}

	void reverseReceipt(ReceivableReceiptEntity receipt, UUID reversalId, UUID actorUserId) {
		receipt.markReversed(reversalId);
		if ("REFUND".equals(receipt.getDirection())) {
			creditBalance = money(creditBalance.add(receipt.getAmount()));
		} else {
			receivedAmount = money(receivedAmount.subtract(receipt.getAmount()));
		}
		recalculateStatus(actorUserId);
	}

	private void recalculateStatus(UUID actorUserId) {
		BigDecimal outstanding = money(grossAmount.subtract(receivedAmount));
		boolean hasPostedRefund = receipts.stream()
				.anyMatch(r -> "REFUND".equals(r.getDirection()) && "POSTED".equals(r.getStatus()));
		if (creditBalance.signum() > 0) {
			status = "CREDIT_PENDING";
		} else if (outstanding.signum() <= 0 && hasPostedRefund) {
			status = "SETTLED";
		} else if (outstanding.signum() <= 0) {
			status = "PAID";
		} else if (receivedAmount.signum() > 0) {
			status = "PARTIALLY_PAID";
		} else {
			status = "OPEN";
		}
		updatedBy = actorUserId;
		updatedAt = Instant.now();
	}

	BigDecimal outstandingAmount() { return money(grossAmount.subtract(receivedAmount).max(BigDecimal.ZERO)); }
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
	BigDecimal getCreditBalance() { return creditBalance; }
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
	private String direction;
	private String status;
	@Column(name = "request_id") private String requestId;
	@Column(name = "credit_note_id") private UUID creditNoteId;
	@Column(name = "reversal_id") private UUID reversalId;
	@Column(name = "reversed_at") private Instant reversedAt;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;

	protected ReceivableReceiptEntity() { }

	ReceivableReceiptEntity(ReceivableInvoiceEntity invoice, String receiptNumber, String direction, BigDecimal amount,
			LocalDate receiptDate, String paymentMethod, String bankReference, String note, String requestId, UUID actorUserId) {
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
		this.direction = direction;
		this.status = "POSTED";
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
	}

	void markReversed(UUID reversalId) {
		this.status = "REVERSED";
		this.reversalId = reversalId;
		this.reversedAt = Instant.now();
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
	String getDirection() { return direction; }
	String getStatus() { return status; }
	Instant getCreatedAt() { return createdAt; }
}

@Entity
@Table(schema = "finance", name = "receivable_credit_notes")
class ReceivableCreditNoteEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "credit_note_number") private String creditNoteNumber;
	@Column(name = "original_invoice_id") private UUID originalInvoiceId;
	@Column(name = "original_invoice_number") private String originalInvoiceNumber;
	@Column(name = "sales_order_id") private UUID salesOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "customer_code") private String customerCode;
	@Column(name = "customer_name") private String customerName;
	private String currency;
	@Column(name = "tax_notice_number") private String taxNoticeNumber;
	@Column(name = "credit_note_date") private LocalDate creditNoteDate;
	@Column(name = "due_date") private LocalDate dueDate;
	@Column(name = "tax_rate") private BigDecimal taxRate;
	@Column(name = "net_amount") private BigDecimal netAmount;
	@Column(name = "tax_amount") private BigDecimal taxAmount;
	@Column(name = "gross_amount") private BigDecimal grossAmount;
	private String reason;
	private String status;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<ReceivableCreditNoteLineEntity> lines = new ArrayList<>();

	protected ReceivableCreditNoteEntity() { }

	ReceivableCreditNoteEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String creditNoteNumber, ReceivableInvoiceEntity original, String taxNoticeNumber,
			LocalDate creditNoteDate, LocalDate dueDate, String reason, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.creditNoteNumber = creditNoteNumber;
		this.originalInvoiceId = original.getId();
		this.originalInvoiceNumber = original.getInvoiceNumber();
		this.salesOrderId = original.getSalesOrderId();
		this.orderNumber = original.getOrderNumber();
		this.customerId = original.getCustomerId();
		this.customerCode = original.getCustomerCode();
		this.customerName = original.getCustomerName();
		this.currency = original.getCurrency();
		this.taxNoticeNumber = trimToNull(taxNoticeNumber);
		this.creditNoteDate = creditNoteDate;
		this.dueDate = dueDate;
		this.taxRate = original.getTaxRate();
		this.netAmount = money(BigDecimal.ZERO);
		this.taxAmount = money(BigDecimal.ZERO);
		this.grossAmount = money(BigDecimal.ZERO);
		this.reason = reason;
		this.status = "POSTED";
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void addLine(ReceivableInvoiceLineEntity originalLine, BigDecimal creditQuantity, BigDecimal overrideUnitPrice) {
		BigDecimal unitPrice = overrideUnitPrice != null ? overrideUnitPrice.setScale(6, RoundingMode.HALF_UP) : originalLine.getUnitPrice();
		ReceivableCreditNoteLineEntity line = new ReceivableCreditNoteLineEntity(this, originalLine, creditQuantity, unitPrice, taxRate);
		lines.add(line);
		netAmount = money(netAmount.add(line.getNetAmount()));
		taxAmount = money(taxAmount.add(line.getTaxAmount()));
		grossAmount = money(netAmount.add(taxAmount));
	}

	private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getCreditNoteNumber() { return creditNoteNumber; }
	UUID getOriginalInvoiceId() { return originalInvoiceId; }
	String getOriginalInvoiceNumber() { return originalInvoiceNumber; }
	UUID getSalesOrderId() { return salesOrderId; }
	String getOrderNumber() { return orderNumber; }
	UUID getCustomerId() { return customerId; }
	String getCustomerCode() { return customerCode; }
	String getCustomerName() { return customerName; }
	String getCurrency() { return currency; }
	String getTaxNoticeNumber() { return taxNoticeNumber; }
	LocalDate getCreditNoteDate() { return creditNoteDate; }
	LocalDate getDueDate() { return dueDate; }
	BigDecimal getTaxRate() { return taxRate; }
	BigDecimal getNetAmount() { return netAmount; }
	BigDecimal getTaxAmount() { return taxAmount; }
	BigDecimal getGrossAmount() { return grossAmount; }
	String getReason() { return reason; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	List<ReceivableCreditNoteLineEntity> getLines() { return lines; }
}

@Entity
@Table(schema = "finance", name = "receivable_credit_note_lines")
class ReceivableCreditNoteLineEntity {
	@Id private UUID id;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "credit_note_id") private ReceivableCreditNoteEntity creditNote;
	@Column(name = "original_invoice_line_id") private UUID originalInvoiceLineId;
	@Column(name = "sales_order_line_id") private UUID salesOrderLineId;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "credit_quantity") private BigDecimal creditQuantity;
	@Column(name = "unit_price") private BigDecimal unitPrice;
	@Column(name = "net_amount") private BigDecimal netAmount;
	@Column(name = "tax_amount") private BigDecimal taxAmount;
	@Column(name = "gross_amount") private BigDecimal grossAmount;

	protected ReceivableCreditNoteLineEntity() { }

	ReceivableCreditNoteLineEntity(ReceivableCreditNoteEntity creditNote, ReceivableInvoiceLineEntity originalLine,
			BigDecimal creditQuantity, BigDecimal unitPrice, BigDecimal taxRate) {
		this.id = UUID.randomUUID();
		this.creditNote = creditNote;
		this.originalInvoiceLineId = originalLine.getId();
		this.salesOrderLineId = originalLine.getSalesOrderLineId();
		this.lineNumber = originalLine.getLineNumber();
		this.materialId = originalLine.getMaterialId();
		this.materialCode = originalLine.getMaterialCode();
		this.materialName = originalLine.getMaterialName();
		this.materialSpecification = originalLine.getMaterialSpecification();
		this.unit = originalLine.getUnit();
		this.creditQuantity = creditQuantity.setScale(4, RoundingMode.HALF_UP);
		this.unitPrice = unitPrice;
		BigDecimal positiveNet = creditQuantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
		this.netAmount = positiveNet.negate();
		this.taxAmount = positiveNet.multiply(taxRate).setScale(2, RoundingMode.HALF_UP).negate();
		this.grossAmount = netAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
	}

	UUID getId() { return id; }
	UUID getOriginalInvoiceLineId() { return originalInvoiceLineId; }
	UUID getSalesOrderLineId() { return salesOrderLineId; }
	int getLineNumber() { return lineNumber; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getCreditQuantity() { return creditQuantity; }
	BigDecimal getUnitPrice() { return unitPrice; }
	BigDecimal getNetAmount() { return netAmount; }
	BigDecimal getTaxAmount() { return taxAmount; }
	BigDecimal getGrossAmount() { return grossAmount; }
}

@Entity
@Table(schema = "finance", name = "receivable_reversals")
class ReceivableReversalEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "reversal_number") private String reversalNumber;
	@Column(name = "receipt_id") private UUID receiptId;
	@Column(name = "receipt_number") private String receiptNumber;
	@Column(name = "invoice_id") private UUID invoiceId;
	@Column(name = "invoice_number") private String invoiceNumber;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "customer_code") private String customerCode;
	@Column(name = "customer_name") private String customerName;
	private String currency;
	private BigDecimal amount;
	@Column(name = "reversed_direction") private String reversedDirection;
	@Column(name = "reversal_date") private LocalDate reversalDate;
	private String reason;
	private String status;
	@Column(name = "request_id") private String requestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;

	protected ReceivableReversalEntity() { }

	ReceivableReversalEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String reversalNumber, ReceivableReceiptEntity receipt, ReceivableInvoiceEntity invoice,
			LocalDate reversalDate, String reason, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.reversalNumber = reversalNumber;
		this.receiptId = receipt.getId();
		this.receiptNumber = receipt.getReceiptNumber();
		this.invoiceId = invoice.getId();
		this.invoiceNumber = invoice.getInvoiceNumber();
		this.customerId = invoice.getCustomerId();
		this.customerCode = invoice.getCustomerCode();
		this.customerName = invoice.getCustomerName();
		this.currency = invoice.getCurrency();
		this.amount = receipt.getAmount();
		this.reversedDirection = receipt.getDirection();
		this.reversalDate = reversalDate;
		this.reason = reason;
		this.status = "POSTED";
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
	}

	UUID getId() { return id; }
	String getReversalNumber() { return reversalNumber; }
	UUID getInvoiceId() { return invoiceId; }
	UUID getReceiptId() { return receiptId; }
	String getReceiptNumber() { return receiptNumber; }
	BigDecimal getAmount() { return amount; }
	String getReversedDirection() { return reversedDirection; }
	LocalDate getReversalDate() { return reversalDate; }
	String getReason() { return reason; }
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
