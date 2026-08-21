package com.guanseq.planning.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.guanseq.masterdata.api.MasterDataReferenceProvider;
import com.guanseq.procurement.api.ScheduledReceiptProvider;
import com.guanseq.production.api.ProductionScheduledReceiptProvider;
import com.guanseq.warehouse.api.StockPositionProvider;

@Entity
@Table(schema = "planning", name = "mrp_runs")
class MrpRunEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "run_number") private String runNumber;
	private String name;
	@Column(name = "horizon_start") private LocalDate horizonStart;
	@Column(name = "horizon_end") private LocalDate horizonEnd;
	private String status;
	@Column(name = "demand_count") private int demandCount;
	@Column(name = "total_quantity") private BigDecimal totalQuantity;
	@Column(name = "exception_count") private int exceptionCount;
	@Column(name = "started_by") private UUID startedBy;
	@Column(name = "started_at") private Instant startedAt;
	@Column(name = "finished_at") private Instant finishedAt;
	@Column(name = "request_id") private String requestId;
	@Version private long version;

	protected MrpRunEntity() {
	}

	MrpRunEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, String runNumber,
			String name, LocalDate horizonStart, LocalDate horizonEnd, int demandCount, BigDecimal totalQuantity,
			UUID startedBy, String requestId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.runNumber = runNumber;
		this.name = name.trim();
		this.horizonStart = horizonStart;
		this.horizonEnd = horizonEnd;
		this.status = "PREPARING";
		this.demandCount = demandCount;
		this.totalQuantity = totalQuantity;
		this.exceptionCount = 0;
		this.startedBy = startedBy;
		this.startedAt = Instant.now();
		this.requestId = requestId;
	}

	void block(int exceptions) {
		this.status = "BLOCKED";
		this.exceptionCount = exceptions;
		this.finishedAt = Instant.now();
	}

	void complete() {
		this.status = "COMPLETED";
		this.exceptionCount = 0;
		this.finishedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getRunNumber() { return runNumber; }
	String getName() { return name; }
	LocalDate getHorizonStart() { return horizonStart; }
	LocalDate getHorizonEnd() { return horizonEnd; }
	String getStatus() { return status; }
	int getDemandCount() { return demandCount; }
	BigDecimal getTotalQuantity() { return totalQuantity; }
	int getExceptionCount() { return exceptionCount; }
	Instant getStartedAt() { return startedAt; }
	Instant getFinishedAt() { return finishedAt; }
	String getRequestId() { return requestId; }
	long getVersion() { return version; }
}

@Entity
@Table(schema = "planning", name = "mrp_run_demands")
class MrpRunDemandEntity {
	@Id private UUID id;
	@Column(name = "run_id") private UUID runId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "demand_id") private UUID demandId;
	@Column(name = "demand_number") private String demandNumber;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "source_number") private String sourceNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	@Column(name = "procurement_type") private String procurementType;
	private String unit;
	private BigDecimal quantity;
	@Column(name = "required_date") private LocalDate requiredDate;
	private String priority;
	private String owner;
	@Column(name = "snapshotted_at") private Instant snapshottedAt;

	protected MrpRunDemandEntity() {
	}

	MrpRunDemandEntity(UUID runId, UUID tenantOrganizationId, IndependentDemandEntity demand, String procurementType) {
		this.id = UUID.randomUUID();
		this.runId = runId;
		this.tenantOrganizationId = tenantOrganizationId;
		this.demandId = demand.getId();
		this.demandNumber = demand.getDemandNumber();
		this.sourceType = demand.getSourceType();
		this.sourceNumber = demand.getSourceNumber();
		this.materialId = demand.getMaterialId();
		this.materialCode = demand.getMaterialCode();
		this.materialName = demand.getMaterialName();
		this.materialSpecification = demand.getMaterialSpecification();
		this.procurementType = procurementType;
		this.unit = demand.getUnit();
		this.quantity = demand.getQuantity();
		this.requiredDate = demand.getRequiredDate();
		this.priority = demand.getPriority();
		this.owner = demand.getOwner();
		this.snapshottedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getDemandId() { return demandId; }
	String getDemandNumber() { return demandNumber; }
	String getSourceType() { return sourceType; }
	String getSourceNumber() { return sourceNumber; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getProcurementType() { return procurementType; }
	String getUnit() { return unit; }
	BigDecimal getQuantity() { return quantity; }
	LocalDate getRequiredDate() { return requiredDate; }
	String getPriority() { return priority; }
	String getOwner() { return owner; }
	Instant getSnapshottedAt() { return snapshottedAt; }
}

@Entity
@Table(schema = "planning", name = "mrp_run_supply_snapshots")
class MrpRunSupplyEntity {
	@Id private UUID id;
	@Column(name = "run_id") private UUID runId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	private String unit;
	@Column(name = "on_hand_quantity") private BigDecimal onHandQuantity;
	@Column(name = "allocated_quantity") private BigDecimal allocatedQuantity;
	@Column(name = "frozen_quantity") private BigDecimal frozenQuantity;
	@Column(name = "available_quantity") private BigDecimal availableQuantity;
	@Column(name = "balance_count") private int balanceCount;
	@Column(name = "snapshotted_at") private Instant snapshottedAt;

	protected MrpRunSupplyEntity() { }

	MrpRunSupplyEntity(UUID runId, UUID tenantId, MasterDataReferenceProvider.MaterialReference material,
			StockPositionProvider.StockPosition position) {
		this.id = UUID.randomUUID(); this.runId = runId; this.tenantOrganizationId = tenantId;
		this.materialId = material.id(); this.materialCode = material.code(); this.materialName = material.name();
		this.unit = material.baseUnit(); this.onHandQuantity = position.onHandQuantity();
		this.allocatedQuantity = position.allocatedQuantity(); this.frozenQuantity = position.frozenQuantity();
		this.availableQuantity = position.availableQuantity(); this.balanceCount = position.balanceCount();
		this.snapshottedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getUnit() { return unit; }
	BigDecimal getOnHandQuantity() { return onHandQuantity; }
	BigDecimal getAllocatedQuantity() { return allocatedQuantity; }
	BigDecimal getFrozenQuantity() { return frozenQuantity; }
	BigDecimal getAvailableQuantity() { return availableQuantity; }
	int getBalanceCount() { return balanceCount; }
	Instant getSnapshottedAt() { return snapshottedAt; }
}

@Entity
@Table(schema = "planning", name = "mrp_run_scheduled_receipt_snapshots")
class MrpRunScheduledReceiptEntity {
	@Id private UUID id;
	@Column(name = "run_id") private UUID runId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "source_order_id") private UUID sourceOrderId;
	@Column(name = "source_order_number") private String sourceOrderNumber;
	@Column(name = "source_line_id") private UUID sourceLineId;
	@Column(name = "supplier_name") private String sourceName;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	private String unit;
	@Column(name = "outstanding_quantity") private BigDecimal outstandingQuantity;
	@Column(name = "expected_receipt_date") private LocalDate expectedReceiptDate;
	@Column(name = "snapshotted_at") private Instant snapshottedAt;

	protected MrpRunScheduledReceiptEntity() { }
	MrpRunScheduledReceiptEntity(UUID runId, UUID tenantId, ScheduledReceiptProvider.ScheduledReceipt receipt) {
		this.id = UUID.randomUUID(); this.runId = runId; this.tenantOrganizationId = tenantId;
		this.sourceType = "PURCHASE_ORDER"; this.sourceOrderId = receipt.orderId();
		this.sourceOrderNumber = receipt.orderNumber(); this.sourceLineId = receipt.lineId();
		this.sourceName = receipt.supplierName(); this.materialId = receipt.materialId();
		this.materialCode = receipt.materialCode(); this.materialName = receipt.materialName(); this.unit = receipt.unit();
		this.outstandingQuantity = receipt.outstandingQuantity(); this.expectedReceiptDate = receipt.expectedReceiptDate();
		this.snapshottedAt = Instant.now();
	}
	MrpRunScheduledReceiptEntity(UUID runId, UUID tenantId, ProductionScheduledReceiptProvider.ScheduledReceipt receipt) {
		this.id = UUID.randomUUID(); this.runId = runId; this.tenantOrganizationId = tenantId;
		this.sourceType = "PRODUCTION_ORDER"; this.sourceOrderId = receipt.orderId();
		this.sourceOrderNumber = receipt.orderNumber(); this.sourceLineId = receipt.orderId();
		this.sourceName = receipt.workshop(); this.materialId = receipt.materialId();
		this.materialCode = receipt.materialCode(); this.materialName = receipt.materialName(); this.unit = receipt.unit();
		this.outstandingQuantity = receipt.outstandingQuantity(); this.expectedReceiptDate = receipt.expectedReceiptDate();
		this.snapshottedAt = Instant.now();
	}
	UUID getId() { return id; } String getSourceType() { return sourceType; } UUID getSourceOrderId() { return sourceOrderId; }
	String getSourceOrderNumber() { return sourceOrderNumber; } UUID getSourceLineId() { return sourceLineId; }
	String getSourceName() { return sourceName; } UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; } String getMaterialName() { return materialName; }
	String getUnit() { return unit; } BigDecimal getOutstandingQuantity() { return outstandingQuantity; }
	LocalDate getExpectedReceiptDate() { return expectedReceiptDate; } Instant getSnapshottedAt() { return snapshottedAt; }
}

@Entity
@Table(schema = "planning", name = "mrp_run_net_requirements")
class MrpRunNetRequirementEntity {
	@Id private UUID id;
	@Column(name = "run_id") private UUID runId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "requirement_level") private int requirementLevel;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "parent_material_id") private UUID parentMaterialId;
	@Column(name = "parent_material_code") private String parentMaterialCode;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "procurement_type") private String procurementType;
	private String unit;
	@Column(name = "gross_quantity") private BigDecimal grossQuantity;
	@Column(name = "available_consumed") private BigDecimal availableConsumed;
	@Column(name = "scheduled_receipt_consumed") private BigDecimal scheduledReceiptConsumed;
	@Column(name = "net_quantity") private BigDecimal netQuantity;
	@Column(name = "required_date") private LocalDate requiredDate;
	@Column(name = "recommended_release_date") private LocalDate recommendedReleaseDate;
	@Column(name = "recommendation_type") private String recommendationType;
	@Column(name = "decision_status") private String decisionStatus;
	@Column(name = "decision_comment") private String decisionComment;
	@Column(name = "decided_by") private UUID decidedBy;
	@Column(name = "decided_at") private Instant decidedAt;
	@Column(name = "converted_order_type") private String convertedOrderType;
	@Column(name = "converted_order_id") private UUID convertedOrderId;
	@Column(name = "converted_order_number") private String convertedOrderNumber;
	@Column(name = "converted_by") private UUID convertedBy;
	@Column(name = "converted_at") private Instant convertedAt;
	@Version private long version;
	@Column(name = "created_at") private Instant createdAt;

	protected MrpRunNetRequirementEntity() { }
	MrpRunNetRequirementEntity(UUID runId, UUID tenantId, int level, String sourceType, UUID parentMaterialId,
			String parentMaterialCode, MasterDataReferenceProvider.MaterialReference material, BigDecimal grossQuantity,
			BigDecimal availableConsumed, BigDecimal scheduledReceiptConsumed, BigDecimal netQuantity,
			LocalDate requiredDate, LocalDate releaseDate, String recommendationType) {
		this.id = UUID.randomUUID(); this.runId = runId; this.tenantOrganizationId = tenantId;
		this.requirementLevel = level; this.sourceType = sourceType; this.parentMaterialId = parentMaterialId;
		this.parentMaterialCode = parentMaterialCode; this.materialId = material.id(); this.materialCode = material.code();
		this.materialName = material.name(); this.procurementType = material.procurementType(); this.unit = material.baseUnit();
		this.grossQuantity = grossQuantity; this.availableConsumed = availableConsumed;
		this.scheduledReceiptConsumed = scheduledReceiptConsumed; this.netQuantity = netQuantity;
		this.requiredDate = requiredDate; this.recommendedReleaseDate = releaseDate;
		this.recommendationType = recommendationType;
		this.decisionStatus = Set.of("PRODUCTION", "PURCHASE", "OUTSOURCE").contains(recommendationType)
				? "PROPOSED" : "NOT_APPLICABLE";
		this.createdAt = Instant.now();
	}

	void decide(String target, String comment, UUID actorId) {
		this.decisionStatus = target; this.decisionComment = comment;
		this.decidedBy = actorId; this.decidedAt = Instant.now();
	}

	void convert(String orderType, UUID orderId, String orderNumber, UUID actorId) {
		this.decisionStatus = "CONVERTED"; this.convertedOrderType = orderType;
		this.convertedOrderId = orderId; this.convertedOrderNumber = orderNumber;
		this.convertedBy = actorId; this.convertedAt = Instant.now();
	}

	UUID getId() { return id; } int getRequirementLevel() { return requirementLevel; } String getSourceType() { return sourceType; }
	UUID getParentMaterialId() { return parentMaterialId; } String getParentMaterialCode() { return parentMaterialCode; }
	UUID getMaterialId() { return materialId; } String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; } String getProcurementType() { return procurementType; }
	String getUnit() { return unit; } BigDecimal getGrossQuantity() { return grossQuantity; }
	BigDecimal getAvailableConsumed() { return availableConsumed; } BigDecimal getScheduledReceiptConsumed() { return scheduledReceiptConsumed; }
	BigDecimal getNetQuantity() { return netQuantity; } LocalDate getRequiredDate() { return requiredDate; }
	LocalDate getRecommendedReleaseDate() { return recommendedReleaseDate; } String getRecommendationType() { return recommendationType; }
	UUID getRunId() { return runId; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getDecisionStatus() { return decisionStatus; } String getDecisionComment() { return decisionComment; }
	Instant getDecidedAt() { return decidedAt; } String getConvertedOrderType() { return convertedOrderType; }
	UUID getConvertedOrderId() { return convertedOrderId; } String getConvertedOrderNumber() { return convertedOrderNumber; }
	Instant getConvertedAt() { return convertedAt; } long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
}

@Entity
@Table(schema = "planning", name = "mrp_suggestion_events")
class MrpSuggestionEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "suggestion_id") private UUID suggestionId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	private String comment;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected MrpSuggestionEventEntity() { }
	MrpSuggestionEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID suggestionId, String action,
			String from, String to, String requestId, String comment, Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.suggestionId = suggestionId; this.action = action;
		this.fromStatus = from; this.toStatus = to; this.requestId = requestId;
		this.comment = comment; this.details = details; this.occurredAt = Instant.now();
	}
	UUID getSuggestionId() { return suggestionId; } String getAction() { return action; }
}

@Entity
@Table(schema = "planning", name = "mrp_run_exceptions")
class MrpRunExceptionEntity {
	@Id private UUID id;
	@Column(name = "run_id") private UUID runId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	private String code;
	private String severity;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	private String message;
	@Column(name = "resolution_path") private String resolutionPath;
	@Column(name = "created_at") private Instant createdAt;

	protected MrpRunExceptionEntity() {
	}

	MrpRunExceptionEntity(UUID runId, UUID tenantOrganizationId, String code, UUID materialId,
			String materialCode, String materialName, String message, String resolutionPath) {
		this.id = UUID.randomUUID();
		this.runId = runId;
		this.tenantOrganizationId = tenantOrganizationId;
		this.code = code;
		this.severity = "BLOCKER";
		this.materialId = materialId;
		this.materialCode = materialCode;
		this.materialName = materialName;
		this.message = message;
		this.resolutionPath = resolutionPath;
		this.createdAt = Instant.now();
	}

	UUID getId() { return id; }
	String getCode() { return code; }
	String getSeverity() { return severity; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMessage() { return message; }
	String getResolutionPath() { return resolutionPath; }
	Instant getCreatedAt() { return createdAt; }
}

@Entity
@Table(schema = "planning", name = "mrp_run_events")
class MrpRunEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "run_id") private UUID runId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected MrpRunEventEntity() {
	}

	MrpRunEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID runId,
			String fromStatus, String toStatus, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.runId = runId;
		this.action = "PRECONDITION_CHECKED";
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}
}
