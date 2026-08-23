package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.guanseq.procurement.api.ProcurementPayableQueryProvider.PayableLine;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider.PayableOrder;

@Entity
@Table(schema = "finance", name = "grir_accruals")
class GrirAccrualEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "accrual_number") private String accrualNumber;
	@Column(name = "fiscal_year") private int fiscalYear;
	@Column(name = "fiscal_period") private int fiscalPeriod;
	@Column(name = "accrual_date") private LocalDate accrualDate;
	private String status;
	@Column(name = "total_net_amount") private BigDecimal totalNetAmount;
	@Column(name = "reversed_by_accrual_id") private UUID reversedByAccrualId;
	@Column(name = "reversal_date") private LocalDate reversalDate;
	@Column(name = "reversal_reason") private String reversalReason;
	private String note;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	@OneToMany(mappedBy = "accrual", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<GrirAccrualLineEntity> lines = new ArrayList<>();

	protected GrirAccrualEntity() { }

	GrirAccrualEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String accrualNumber, int fiscalYear, int fiscalPeriod, LocalDate accrualDate,
			String note, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.accrualNumber = accrualNumber;
		this.fiscalYear = fiscalYear;
		this.fiscalPeriod = fiscalPeriod;
		this.accrualDate = accrualDate;
		this.status = "POSTED";
		this.totalNetAmount = BigDecimal.ZERO.setScale(2);
		this.note = note;
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void addLine(PayableOrder order, PayableLine source, BigDecimal receivedQuantity,
			BigDecimal invoicedQuantity) {
		GrirAccrualLineEntity line = new GrirAccrualLineEntity(this, order, source,
				receivedQuantity, invoicedQuantity);
		lines.add(line);
		totalNetAmount = totalNetAmount.add(line.getNetAmount()).setScale(2, java.math.RoundingMode.HALF_UP);
	}

	void markReversedBy(UUID newAccrualId, LocalDate reversalDate, UUID actorUserId) {
		this.status = "REVERSED";
		this.reversedByAccrualId = newAccrualId;
		this.reversalDate = reversalDate;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void manuallyReverse(LocalDate reversalDate, String reason, UUID actorUserId) {
		this.status = "REVERSED";
		this.reversalDate = reversalDate;
		this.reversalReason = reason;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getAccrualNumber() { return accrualNumber; }
	int getFiscalYear() { return fiscalYear; }
	int getFiscalPeriod() { return fiscalPeriod; }
	LocalDate getAccrualDate() { return accrualDate; }
	String getStatus() { return status; }
	BigDecimal getTotalNetAmount() { return totalNetAmount; }
	UUID getReversedByAccrualId() { return reversedByAccrualId; }
	LocalDate getReversalDate() { return reversalDate; }
	String getReversalReason() { return reversalReason; }
	String getNote() { return note; }
	String getRequestId() { return requestId; }
	long getVersion() { return version; }
	UUID getCreatedBy() { return createdBy; }
	Instant getCreatedAt() { return createdAt; }
	List<GrirAccrualLineEntity> getLines() { return lines; }
}

@Entity
@Table(schema = "finance", name = "grir_accrual_lines")
class GrirAccrualLineEntity {

	@Id private UUID id;
	@Column(name = "accrual_id", nullable = false) private UUID accrualId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "purchase_order_id") private UUID purchaseOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "supplier_id") private UUID supplierId;
	@Column(name = "supplier_code") private String supplierCode;
	@Column(name = "supplier_name") private String supplierName;
	@Column(name = "purchase_order_line_id") private UUID purchaseOrderLineId;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "received_quantity") private BigDecimal receivedQuantity;
	@Column(name = "invoiced_quantity") private BigDecimal invoicedQuantity;
	@Column(name = "accrued_quantity") private BigDecimal accruedQuantity;
	@Column(name = "unit_price") private BigDecimal unitPrice;
	@Column(name = "net_amount") private BigDecimal netAmount;

	@jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "accrual_id", insertable = false, updatable = false)
	private GrirAccrualEntity accrual;

	protected GrirAccrualLineEntity() { }

	GrirAccrualLineEntity(GrirAccrualEntity parent, PayableOrder order, PayableLine source,
			BigDecimal receivedQuantity, BigDecimal invoicedQuantity) {
		this.id = UUID.randomUUID();
		this.accrualId = parent.getId();
		this.tenantOrganizationId = parent.getTenantOrganizationId();
		this.purchaseOrderId = order.id();
		this.orderNumber = order.orderNumber();
		this.supplierId = order.supplierId();
		this.supplierCode = order.supplierCode();
		this.supplierName = order.supplierName();
		this.purchaseOrderLineId = source.id();
		this.lineNumber = source.lineNumber();
		this.materialId = source.materialId();
		this.materialCode = source.materialCode();
		this.materialName = source.materialName();
		this.materialSpecification = source.materialSpecification();
		this.unit = source.unit();
		this.receivedQuantity = receivedQuantity.setScale(4, java.math.RoundingMode.HALF_UP);
		this.invoicedQuantity = invoicedQuantity.setScale(4, java.math.RoundingMode.HALF_UP);
		this.accruedQuantity = receivedQuantity.subtract(invoicedQuantity)
				.setScale(4, java.math.RoundingMode.HALF_UP);
		this.unitPrice = source.unitPrice().setScale(6, java.math.RoundingMode.HALF_UP);
		this.netAmount = this.accruedQuantity.multiply(this.unitPrice)
				.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	UUID getId() { return id; }
	UUID getPurchaseOrderId() { return purchaseOrderId; }
	String getOrderNumber() { return orderNumber; }
	UUID getSupplierId() { return supplierId; }
	String getSupplierCode() { return supplierCode; }
	String getSupplierName() { return supplierName; }
	UUID getPurchaseOrderLineId() { return purchaseOrderLineId; }
	int getLineNumber() { return lineNumber; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getReceivedQuantity() { return receivedQuantity; }
	BigDecimal getInvoicedQuantity() { return invoicedQuantity; }
	BigDecimal getAccruedQuantity() { return accruedQuantity; }
	BigDecimal getUnitPrice() { return unitPrice; }
	BigDecimal getNetAmount() { return netAmount; }
}

@Entity
@Table(schema = "finance", name = "grir_accrual_events")
class GrirAccrualEventEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "accrual_id") private UUID accrualId;
	private String action;
	@Column(name = "request_id") private String requestId;
	@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
	private java.util.Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected GrirAccrualEventEntity() { }

	GrirAccrualEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID accrualId, String action, String requestId, java.util.Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.accrualId = accrualId;
		this.action = action;
		this.requestId = requestId;
		this.details = details == null ? java.util.Map.of() : details;
		this.occurredAt = Instant.now();
	}
}
