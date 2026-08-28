package com.guanseq.sales.internal;

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
@Table(schema = "sales", name = "orders")
class SalesOrderEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "customer_code") private String customerCode;
	@Column(name = "customer_name") private String customerName;
	private String currency;
	@Column(name = "tax_rate") private BigDecimal taxRate;
	@Column(name = "requested_delivery_date") private LocalDate requestedDeliveryDate;
	@Column(name = "promised_delivery_date") private LocalDate promisedDeliveryDate;
	private String owner;
	private String status;
	@Column(name = "total_net_amount") private BigDecimal totalNetAmount;
	@Column(name = "total_tax_amount") private BigDecimal totalTaxAmount;
	@Column(name = "total_gross_amount") private BigDecimal totalGrossAmount;
	@Column(name = "rejection_reason") private String rejectionReason;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<SalesOrderLineEntity> lines = new ArrayList<>();

	protected SalesOrderEntity() {
	}

	SalesOrderEntity(UUID tenantOrganizationId, UUID owningOrganizationId, String orderNumber, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.orderNumber = orderNumber;
		this.status = "DRAFT";
		this.totalNetAmount = BigDecimal.ZERO.setScale(2);
		this.totalTaxAmount = BigDecimal.ZERO.setScale(2);
		this.totalGrossAmount = BigDecimal.ZERO.setScale(2);
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void updateHeader(UUID customerId, String customerCode, String customerName, String currency, BigDecimal taxRate,
			LocalDate requestedDeliveryDate, LocalDate promisedDeliveryDate, String owner, UUID actorUserId) {
		this.customerId = customerId;
		this.customerCode = customerCode;
		this.customerName = customerName;
		this.currency = currency;
		this.taxRate = taxRate;
		this.requestedDeliveryDate = requestedDeliveryDate;
		this.promisedDeliveryDate = promisedDeliveryDate;
		this.owner = owner.trim();
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
		if ("REJECTED".equals(this.status)) this.rejectionReason = null;
	}

	void replaceLines(List<SalesOrderLineEntity> replacements, UUID actorUserId) {
		this.lines.clear();
		this.lines.addAll(replacements);
		this.totalNetAmount = replacements.stream().map(SalesOrderLineEntity::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		this.totalTaxAmount = replacements.stream().map(SalesOrderLineEntity::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		this.totalGrossAmount = replacements.stream().map(SalesOrderLineEntity::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void transition(String targetStatus, String comment, UUID actorUserId) {
		this.status = targetStatus;
		this.rejectionReason = "REJECTED".equals(targetStatus) ? comment : null;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void applyShipment(java.util.Map<UUID, BigDecimal> shippedByLine, UUID actorUserId) {
		for (SalesOrderLineEntity line : lines) {
			BigDecimal shipped = shippedByLine.getOrDefault(line.getId(), BigDecimal.ZERO);
			if (shipped.signum() != 0) line.applyShippedQuantity(shipped);
		}
		updateFulfillmentStatus();
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void applyReturn(java.util.Map<UUID, BigDecimal> returnedByLine, UUID actorUserId) {
		for (SalesOrderLineEntity line : lines) {
			BigDecimal returned = returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO);
			if (returned.signum() != 0) line.applyReturnedQuantity(returned);
		}
		updateFulfillmentStatus();
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void reverseReturn(java.util.Map<UUID, BigDecimal> returnedByLine, UUID actorUserId) {
		for (SalesOrderLineEntity line : lines) {
			BigDecimal returned = returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO);
			if (returned.signum() != 0) line.reverseReturnedQuantity(returned);
		}
		updateFulfillmentStatus();
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	private void updateFulfillmentStatus() {
		boolean anyGrossShipment = lines.stream().anyMatch(line -> line.getDeliveredQuantity().signum() > 0);
		boolean anyNetShipment = lines.stream().anyMatch(line -> line.getNetDeliveredQuantity().signum() > 0);
		boolean anyReturn = lines.stream().anyMatch(line -> line.getReturnedQuantity().signum() > 0);
		boolean allShipped = lines.stream().allMatch(line -> line.getNetDeliveredQuantity().compareTo(line.getQuantity()) >= 0);
		if (allShipped) this.status = "SHIPPED";
		else if (!anyNetShipment && anyGrossShipment) this.status = "RETURNED";
		else if (anyReturn) this.status = "PARTIALLY_RETURNED";
		else if (anyNetShipment) this.status = "PARTIALLY_SHIPPED";
		else this.status = "RELEASED";
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	String getOrderNumber() { return orderNumber; }
	UUID getCustomerId() { return customerId; }
	String getCustomerCode() { return customerCode; }
	String getCustomerName() { return customerName; }
	String getCurrency() { return currency; }
	BigDecimal getTaxRate() { return taxRate; }
	LocalDate getRequestedDeliveryDate() { return requestedDeliveryDate; }
	LocalDate getPromisedDeliveryDate() { return promisedDeliveryDate; }
	String getOwner() { return owner; }
	String getStatus() { return status; }
	BigDecimal getTotalNetAmount() { return totalNetAmount; }
	BigDecimal getTotalTaxAmount() { return totalTaxAmount; }
	BigDecimal getTotalGrossAmount() { return totalGrossAmount; }
	String getRejectionReason() { return rejectionReason; }
	long getVersion() { return version; }
	Instant getUpdatedAt() { return updatedAt; }
	List<SalesOrderLineEntity> getLines() { return lines; }
}

@Entity
@Table(schema = "sales", name = "order_lines")
class SalesOrderLineEntity {

	@Id private UUID id;
	@jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "order_id")
	private SalesOrderEntity order;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	private BigDecimal quantity;
	@Column(name = "unit_price") private BigDecimal unitPrice;
	@Column(name = "net_amount") private BigDecimal netAmount;
	@Column(name = "tax_amount") private BigDecimal taxAmount;
	@Column(name = "gross_amount") private BigDecimal grossAmount;
	@Column(name = "delivered_quantity") private BigDecimal deliveredQuantity = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
	@Column(name = "returned_quantity") private BigDecimal returnedQuantity = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

	protected SalesOrderLineEntity() {
	}

	SalesOrderLineEntity(SalesOrderEntity order, int lineNumber, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate) {
		this.id = UUID.randomUUID();
		this.order = order;
		this.lineNumber = lineNumber;
		this.materialId = materialId;
		this.materialCode = materialCode;
		this.materialName = materialName;
		this.materialSpecification = materialSpecification;
		this.unit = unit;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.netAmount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
		this.taxAmount = this.netAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
		this.grossAmount = this.netAmount.add(this.taxAmount).setScale(2, RoundingMode.HALF_UP);
	}

	UUID getId() { return id; }
	int getLineNumber() { return lineNumber; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getQuantity() { return quantity; }
	BigDecimal getUnitPrice() { return unitPrice; }
	BigDecimal getNetAmount() { return netAmount; }
	BigDecimal getTaxAmount() { return taxAmount; }
	BigDecimal getGrossAmount() { return grossAmount; }
	BigDecimal getDeliveredQuantity() { return deliveredQuantity; }
	BigDecimal getReturnedQuantity() { return returnedQuantity; }
	BigDecimal getNetDeliveredQuantity() { return deliveredQuantity.subtract(returnedQuantity).setScale(4, RoundingMode.HALF_UP); }
	void applyShippedQuantity(BigDecimal quantity) { this.deliveredQuantity = this.deliveredQuantity.add(quantity).setScale(4, RoundingMode.HALF_UP); }
	void applyReturnedQuantity(BigDecimal quantity) {
		BigDecimal next = returnedQuantity.add(quantity).setScale(4, RoundingMode.HALF_UP);
		if (next.compareTo(deliveredQuantity) > 0) throw new IllegalStateException("累计退货数量不能超过累计毛发货数量");
		this.returnedQuantity = next;
	}
	void reverseReturnedQuantity(BigDecimal quantity) {
		BigDecimal next = returnedQuantity.subtract(quantity).setScale(4, RoundingMode.HALF_UP);
		if (next.signum() < 0) throw new IllegalStateException("累计退货数量不能小于零");
		this.returnedQuantity = next;
	}
}

@Entity
@Table(schema = "sales", name = "change_events")
class SalesOrderChangeEventEntity {
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

	protected SalesOrderChangeEventEntity() {
	}

	SalesOrderChangeEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID orderId,
			String action, String fromStatus, String toStatus, String requestId, String comment, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.orderId = orderId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.requestId = requestId;
		this.comment = comment;
		this.details = details;
		this.occurredAt = Instant.now();
	}
}
