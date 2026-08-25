package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "finance", name = "item_standard_costs")
class ItemStandardCostEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	private String unit;
	private String currency;
	@Column(name = "unit_cost") private BigDecimal unitCost;
	@Column(name = "effective_date") private LocalDate effectiveDate;
	private String status;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected ItemStandardCostEntity() { }

	UUID getMaterialId() { return materialId; }
	BigDecimal getUnitCost() { return unitCost; }
	String getCurrency() { return currency; }
	LocalDate getEffectiveDate() { return effectiveDate; }
}

@Entity
@Table(schema = "finance", name = "order_profit_settlements")
class OrderProfitSettlementEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "settlement_number") private String settlementNumber;
	@Column(name = "sales_order_id") private UUID salesOrderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "customer_id") private UUID customerId;
	@Column(name = "customer_code") private String customerCode;
	@Column(name = "customer_name") private String customerName;
	private String currency;
	@Column(name = "shipped_quantity") private BigDecimal shippedQuantity;
	private BigDecimal revenue;
	@Column(name = "material_cost") private BigDecimal materialCost;
	@Column(name = "labor_cost") private BigDecimal laborCost;
	@Column(name = "overhead_cost") private BigDecimal overheadCost;
	@Column(name = "processing_cost") private BigDecimal processingCost;
	@Column(name = "total_cost") private BigDecimal totalCost;
	@Column(name = "gross_profit") private BigDecimal grossProfit;
	@Column(name = "gross_margin") private BigDecimal grossMargin;
	@Column(name = "cost_basis") private String costBasis;
	@Column(name = "cost_status") private String costStatus;
	private String status;
	@Column(name = "settlement_version") private int settlementVersion;
	@Column(name = "supersedes_id") private UUID supersedesId;
	@Column(name = "impact_reason") private String impactReason;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "missing_items") private List<String> missingItems = new ArrayList<>();
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@Column(name = "settled_at") private Instant settledAt;
	@OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<OrderProfitSettlementLineEntity> lines = new ArrayList<>();

	protected OrderProfitSettlementEntity() { }

	OrderProfitSettlementEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String settlementNumber, SalesOrderSnapshot order, BigDecimal shippedQuantity, BigDecimal revenue,
			BigDecimal materialCost, BigDecimal laborCost, BigDecimal overheadCost, String costStatus, List<String> missingItems,
			String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.settlementNumber = settlementNumber;
		this.salesOrderId = order.id();
		this.orderNumber = order.orderNumber();
		this.customerId = order.customerId();
		this.customerCode = order.customerCode();
		this.customerName = order.customerName();
		this.currency = order.currency();
		this.shippedQuantity = shippedQuantity;
		this.revenue = revenue;
		this.materialCost = materialCost;
		this.laborCost = laborCost;
		this.overheadCost = overheadCost;
		this.processingCost = laborCost.add(overheadCost);
		this.totalCost = materialCost.add(processingCost);
		this.grossProfit = revenue.subtract(totalCost);
		this.grossMargin = revenue.signum() == 0 ? null : grossProfit.divide(revenue, 6, java.math.RoundingMode.HALF_UP);
		this.costBasis = "ACTUAL_MATERIAL_ISSUE_WITH_STANDARD_COST;STANDARD_OPERATION_TIME_WITH_EFFECTIVE_WORK_CENTER_RATE";
		this.costStatus = costStatus;
		this.status = "SETTLED";
		this.settlementVersion = 1;
		this.missingItems = new ArrayList<>(missingItems);
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = createdAt;
		this.settledAt = createdAt;
	}

	/** 重算时创建新版本快照，supersedesId 指向被替代的旧版本，version 为旧版本+1。 */
	OrderProfitSettlementEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String settlementNumber, SalesOrderSnapshot order, BigDecimal shippedQuantity, BigDecimal revenue,
			BigDecimal materialCost, BigDecimal laborCost, BigDecimal overheadCost, String costStatus, List<String> missingItems,
			String requestId, UUID actorUserId, int settlementVersion, UUID supersedesId) {
		this(tenantOrganizationId, owningOrganizationId, workspaceId, settlementNumber, order, shippedQuantity,
				revenue, materialCost, laborCost, overheadCost, costStatus, missingItems, requestId, actorUserId);
		this.settlementVersion = settlementVersion;
		this.supersedesId = supersedesId;
		this.status = "SETTLED";
	}

	void addLine(OrderProfitSettlementLineEntity line) { this.lines.add(line); }

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getSettlementNumber() { return settlementNumber; }
	UUID getSalesOrderId() { return salesOrderId; }
	String getOrderNumber() { return orderNumber; }
	UUID getCustomerId() { return customerId; }
	String getCustomerCode() { return customerCode; }
	String getCustomerName() { return customerName; }
	String getCurrency() { return currency; }
	BigDecimal getShippedQuantity() { return shippedQuantity; }
	BigDecimal getRevenue() { return revenue; }
	BigDecimal getMaterialCost() { return materialCost; }
	BigDecimal getLaborCost() { return laborCost; }
	BigDecimal getOverheadCost() { return overheadCost; }
	BigDecimal getProcessingCost() { return processingCost; }
	BigDecimal getTotalCost() { return totalCost; }
	BigDecimal getGrossProfit() { return grossProfit; }
	BigDecimal getGrossMargin() { return grossMargin; }
	String getCostBasis() { return costBasis; }
	String getCostStatus() { return costStatus; }
	String getStatus() { return status; }
	int getSettlementVersion() { return settlementVersion; }
	UUID getSupersedesId() { return supersedesId; }
	String getImpactReason() { return impactReason; }
	void markImpacted(String reason) {
		this.status = "IMPACTED";
		this.impactReason = reason;
		this.updatedAt = Instant.now();
	}
	void markSuperseded(UUID actorUserId) {
		this.status = "SUPERSEDED";
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}
	List<String> getMissingItems() { return missingItems; }
	String getRequestId() { return requestId; }
	long getVersion() { return version; }
	Instant getSettledAt() { return settledAt; }
	List<OrderProfitSettlementLineEntity> getLines() { return lines; }
}

@Entity
@Table(schema = "finance", name = "order_profit_settlement_lines")
class OrderProfitSettlementLineEntity {
	@Id private UUID id;
	@jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "settlement_id")
	private OrderProfitSettlementEntity settlement;
	@Column(name = "sales_order_line_id") private UUID salesOrderLineId;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "production_order_id") private UUID productionOrderId;
	@Column(name = "production_order_number") private String productionOrderNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "ordered_quantity") private BigDecimal orderedQuantity;
	@Column(name = "shipped_quantity") private BigDecimal shippedQuantity;
	@Column(name = "accepted_quantity") private BigDecimal acceptedQuantity;
	@Column(name = "consumed_quantity") private BigDecimal consumedQuantity;
	@Column(name = "unit_price") private BigDecimal unitPrice;
	private BigDecimal revenue;
	@Column(name = "material_cost") private BigDecimal materialCost;
	@Column(name = "labor_cost") private BigDecimal laborCost;
	@Column(name = "overhead_cost") private BigDecimal overheadCost;
	@Column(name = "processing_cost") private BigDecimal processingCost;
	@Column(name = "total_cost") private BigDecimal totalCost;
	@Column(name = "gross_profit") private BigDecimal grossProfit;
	@Column(name = "gross_margin") private BigDecimal grossMargin;
	@Column(name = "cost_status") private String costStatus;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "cost_details") private java.util.Map<String, Object> costDetails = new java.util.LinkedHashMap<>();

	protected OrderProfitSettlementLineEntity() { }

	OrderProfitSettlementLineEntity(OrderProfitSettlementEntity settlement, SalesOrderLineSnapshot line,
			ProductionCostSnapshot cost, BigDecimal revenue, BigDecimal materialCost, BigDecimal laborCost,
			BigDecimal overheadCost, String costStatus,
			java.util.Map<String, Object> costDetails) {
		this.id = UUID.randomUUID();
		this.settlement = settlement;
		this.salesOrderLineId = line.id();
		this.lineNumber = line.lineNumber();
		this.productionOrderId = cost == null ? null : cost.productionOrderId();
		this.productionOrderNumber = cost == null ? null : cost.productionOrderNumber();
		this.materialId = line.materialId();
		this.materialCode = line.materialCode();
		this.materialName = line.materialName();
		this.materialSpecification = line.materialSpecification();
		this.unit = line.unit();
		this.orderedQuantity = line.orderedQuantity();
		this.shippedQuantity = line.shippedQuantity();
		this.acceptedQuantity = cost == null ? null : cost.acceptedQuantity();
		this.consumedQuantity = cost == null ? null : cost.consumedQuantity();
		this.unitPrice = line.unitPrice();
		this.revenue = revenue;
		this.materialCost = materialCost;
		this.laborCost = laborCost;
		this.overheadCost = overheadCost;
		this.processingCost = laborCost.add(overheadCost);
		this.totalCost = materialCost.add(processingCost);
		this.grossProfit = revenue.subtract(totalCost);
		this.grossMargin = revenue.signum() == 0 ? null : grossProfit.divide(revenue, 6, java.math.RoundingMode.HALF_UP);
		this.costStatus = costStatus;
		this.costDetails = new java.util.LinkedHashMap<>(costDetails);
	}

	UUID getId() { return id; }
	UUID getSalesOrderLineId() { return salesOrderLineId; }
	int getLineNumber() { return lineNumber; }
	UUID getProductionOrderId() { return productionOrderId; }
	String getProductionOrderNumber() { return productionOrderNumber; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getOrderedQuantity() { return orderedQuantity; }
	BigDecimal getShippedQuantity() { return shippedQuantity; }
	BigDecimal getAcceptedQuantity() { return acceptedQuantity; }
	BigDecimal getConsumedQuantity() { return consumedQuantity; }
	BigDecimal getUnitPrice() { return unitPrice; }
	BigDecimal getRevenue() { return revenue; }
	BigDecimal getMaterialCost() { return materialCost; }
	BigDecimal getLaborCost() { return laborCost; }
	BigDecimal getOverheadCost() { return overheadCost; }
	BigDecimal getProcessingCost() { return processingCost; }
	BigDecimal getTotalCost() { return totalCost; }
	BigDecimal getGrossProfit() { return grossProfit; }
	BigDecimal getGrossMargin() { return grossMargin; }
	String getCostStatus() { return costStatus; }
	java.util.Map<String, Object> getCostDetails() { return costDetails; }
}

@Entity
@Table(schema = "finance", name = "order_profit_events")
class OrderProfitEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "settlement_id") private UUID settlementId;
	private String action;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "cost_status") private String costStatus;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private java.util.Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected OrderProfitEventEntity() { }

	OrderProfitEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID settlementId,
			String action, String toStatus, String costStatus, String requestId, java.util.Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.settlementId = settlementId;
		this.action = action;
		this.toStatus = toStatus;
		this.costStatus = costStatus;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}
}

record SalesOrderSnapshot(UUID id, String orderNumber, UUID customerId, String customerCode, String customerName,
		String currency, String status, List<SalesOrderLineSnapshot> lines) { }
record SalesOrderLineSnapshot(UUID id, int lineNumber, UUID materialId, String materialCode, String materialName,
		String materialSpecification, String unit, BigDecimal orderedQuantity, BigDecimal shippedQuantity, BigDecimal unitPrice) { }
record ProductionCostSnapshot(UUID productionOrderId, String productionOrderNumber, BigDecimal acceptedQuantity,
		BigDecimal consumedQuantity, List<ConsumedComponent> components, List<CompletedOperationSnapshot> operations) { }
record ConsumedComponent(UUID materialId, String materialCode, String materialName, String materialSpecification,
		String unit, BigDecimal netQuantity, BigDecimal unitCost, BigDecimal amount) { }
record CompletedOperationSnapshot(UUID taskId, String taskNumber, String operationCode, String operationName,
		String workCenterCode, String workCenterName, BigDecimal setupMinutes, BigDecimal runMinutesPerUnit,
		BigDecimal completedQuantity, Instant completedAt, BigDecimal approvedActualLaborMinutes) { }
