package com.guanseq.production.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.guanseq.product.api.RoutingReferenceProvider.EffectiveOperation;
import com.guanseq.product.api.RoutingReferenceProvider.EffectiveRouting;

@Entity
@Table(schema = "production", name = "operation_tasks")
class OperationTaskEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "task_number") private String taskNumber;
	@Column(name = "order_id") private UUID orderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "planned_quantity") private BigDecimal plannedQuantity;
	private String workshop;
	@Column(name = "routing_id") private UUID routingId;
	@Column(name = "routing_number") private String routingNumber;
	@Column(name = "routing_version_code") private String routingVersionCode;
	@Column(name = "source_operation_id") private UUID sourceOperationId;
	@Column(name = "sequence_number") private int sequenceNumber;
	@Column(name = "operation_code") private String operationCode;
	@Column(name = "operation_name") private String operationName;
	@Column(name = "work_center_code") private String workCenterCode;
	@Column(name = "work_center_name") private String workCenterName;
	@Column(name = "setup_minutes") private BigDecimal setupMinutes;
	@Column(name = "run_minutes_per_unit") private BigDecimal runMinutesPerUnit;
	@Column(name = "queue_minutes") private BigDecimal queueMinutes;
	@Column(name = "inspection_required") private boolean inspectionRequired;
	@Column(name = "instruction_summary") private String instructionSummary;
	private String status;
	@Column(name = "started_at") private Instant startedAt;
	@Column(name = "completed_at") private Instant completedAt;
	@Column(name = "completed_quantity") private BigDecimal completedQuantity;
	@Column(name = "shift_name") private String shiftName;
	@Column(name = "operator_name") private String operatorName;
	private String note;
	@Column(name = "start_request_id") private String startRequestId;
	@Column(name = "complete_request_id") private String completeRequestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected OperationTaskEntity() { }

	OperationTaskEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String taskNumber,
			ProductionOrderEntity order, EffectiveRouting routing, EffectiveOperation operation, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.taskNumber = taskNumber; this.orderId = order.getId();
		this.orderNumber = order.getOrderNumber(); this.materialId = order.getMaterialId();
		this.materialCode = order.getMaterialCode(); this.materialName = order.getMaterialName();
		this.materialSpecification = order.getMaterialSpecification(); this.unit = order.getUnit();
		this.plannedQuantity = order.getPlannedQuantity(); this.workshop = order.getWorkshop();
		this.routingId = routing.routingId(); this.routingNumber = routing.routingNumber();
		this.routingVersionCode = routing.versionCode(); this.sourceOperationId = operation.operationId();
		this.sequenceNumber = operation.sequenceNumber(); this.operationCode = operation.operationCode();
		this.operationName = operation.operationName(); this.workCenterCode = operation.workCenterCode();
		this.workCenterName = operation.workCenterName(); this.setupMinutes = operation.setupMinutes();
		this.runMinutesPerUnit = operation.runMinutesPerUnit(); this.queueMinutes = operation.queueMinutes();
		this.inspectionRequired = operation.inspectionRequired();
		this.instructionSummary = operation.instructionSummary();
		this.status = "PENDING"; this.createdBy = actorId; this.createdAt = Instant.now();
		this.updatedBy = actorId; this.updatedAt = createdAt;
	}

	void start(String shiftName, String operatorName, String note, String requestId, UUID actorId) {
		if (!"PENDING".equals(status)) throw new IllegalStateException("只有待开工工序可以开始执行");
		this.status = "IN_PROGRESS"; this.shiftName = shiftName; this.operatorName = operatorName;
		this.note = note; this.startRequestId = requestId; this.startedAt = Instant.now();
		this.updatedBy = actorId; this.updatedAt = this.startedAt;
	}

	void complete(BigDecimal quantity, String shiftName, String operatorName, String note, String requestId, UUID actorId) {
		if (!"IN_PROGRESS".equals(status)) throw new IllegalStateException("只有执行中工序可以登记完工");
		if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("完工数量必须大于零");
		if (quantity.compareTo(plannedQuantity) > 0) throw new IllegalStateException("工序完工数量不能超过订单计划数量");
		this.status = "COMPLETED"; this.completedQuantity = quantity; this.completeRequestId = requestId;
		if (shiftName != null && !shiftName.isBlank()) this.shiftName = shiftName;
		if (operatorName != null && !operatorName.isBlank()) this.operatorName = operatorName;
		if (note != null && !note.isBlank()) this.note = note;
		this.completedAt = Instant.now(); this.updatedBy = actorId; this.updatedAt = this.completedAt;
	}

	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; } UUID getWorkspaceId() { return workspaceId; }
	String getTaskNumber() { return taskNumber; } UUID getOrderId() { return orderId; } String getOrderNumber() { return orderNumber; }
	UUID getMaterialId() { return materialId; } String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; } String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; } BigDecimal getPlannedQuantity() { return plannedQuantity; } String getWorkshop() { return workshop; }
	UUID getRoutingId() { return routingId; } String getRoutingNumber() { return routingNumber; } String getRoutingVersionCode() { return routingVersionCode; }
	UUID getSourceOperationId() { return sourceOperationId; } int getSequenceNumber() { return sequenceNumber; }
	String getOperationCode() { return operationCode; } String getOperationName() { return operationName; }
	String getWorkCenterCode() { return workCenterCode; } String getWorkCenterName() { return workCenterName; }
	BigDecimal getSetupMinutes() { return setupMinutes; } BigDecimal getRunMinutesPerUnit() { return runMinutesPerUnit; }
	BigDecimal getQueueMinutes() { return queueMinutes; } boolean isInspectionRequired() { return inspectionRequired; }
	String getInstructionSummary() { return instructionSummary; }
	String getStatus() { return status; } Instant getStartedAt() { return startedAt; } Instant getCompletedAt() { return completedAt; }
	BigDecimal getCompletedQuantity() { return completedQuantity; } String getShiftName() { return shiftName; }
	String getOperatorName() { return operatorName; } String getNote() { return note; }
	String getStartRequestId() { return startRequestId; } String getCompleteRequestId() { return completeRequestId; }
	long getVersion() { return version; } UUID getCreatedBy() { return createdBy; }
	Instant getCreatedAt() { return createdAt; } UUID getUpdatedBy() { return updatedBy; } Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "production", name = "operation_events")
class OperationEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "task_id") private UUID taskId;
	@Column(name = "order_id") private UUID orderId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	private String comment;
	private String source;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected OperationEventEntity() { }
	OperationEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID taskId, UUID orderId, String action,
			String fromStatus, String toStatus, String requestId, String comment, String source, Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.taskId = taskId; this.orderId = orderId; this.action = action;
		this.fromStatus = fromStatus; this.toStatus = toStatus; this.requestId = requestId; this.comment = comment; this.source = source;
		this.details = details == null ? Map.of() : details; this.occurredAt = Instant.now();
	}

	UUID getId() { return id; } UUID getTaskId() { return taskId; } String getAction() { return action; }
	String getFromStatus() { return fromStatus; } String getToStatus() { return toStatus; }
	String getRequestId() { return requestId; } String getComment() { return comment; }
	String getSource() { return source; }
	Instant getOccurredAt() { return occurredAt; }
}
