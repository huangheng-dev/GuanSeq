package com.guanseq.warehouse.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "warehouse", name = "putaway_tasks")
class PutawayTaskEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "task_number") private String taskNumber;
	private String status;
	@Column(name = "source_balance_id") private UUID sourceBalanceId;
	@Column(name = "source_warehouse_id") private UUID sourceWarehouseId;
	@Column(name = "source_warehouse_code") private String sourceWarehouseCode;
	@Column(name = "source_warehouse_name") private String sourceWarehouseName;
	@Column(name = "source_location_id") private UUID sourceLocationId;
	@Column(name = "source_location_code") private String sourceLocationCode;
	@Column(name = "source_location_name") private String sourceLocationName;
	@Column(name = "target_location_id") private UUID targetLocationId;
	@Column(name = "target_location_code") private String targetLocationCode;
	@Column(name = "target_location_name") private String targetLocationName;
	@Column(name = "target_balance_id") private UUID targetBalanceId;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	@Column(name = "lot_number") private String lotNumber;
	private String unit;
	@Column(name = "quality_status") private String qualityStatus;
	private BigDecimal quantity;
	@Column(name = "source_out_movement_id") private UUID sourceOutMovementId;
	@Column(name = "source_out_movement_number") private String sourceOutMovementNumber;
	@Column(name = "target_in_movement_id") private UUID targetInMovementId;
	@Column(name = "target_in_movement_number") private String targetInMovementNumber;
	@Column(name = "reverse_out_movement_id") private UUID reverseOutMovementId;
	@Column(name = "reverse_out_movement_number") private String reverseOutMovementNumber;
	@Column(name = "reverse_in_movement_id") private UUID reverseInMovementId;
	@Column(name = "reverse_in_movement_number") private String reverseInMovementNumber;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_by_username") private String createdByUsername;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "completed_by") private UUID completedBy;
	@Column(name = "completed_by_username") private String completedByUsername;
	@Column(name = "completed_at") private Instant completedAt;
	@Column(name = "cancelled_by") private UUID cancelledBy;
	@Column(name = "cancelled_by_username") private String cancelledByUsername;
	@Column(name = "cancelled_at") private Instant cancelledAt;
	@Column(name = "cancellation_reason") private String cancellationReason;
	@Column(name = "reversed_by") private UUID reversedBy;
	@Column(name = "reversed_by_username") private String reversedByUsername;
	@Column(name = "reversed_at") private Instant reversedAt;
	@Column(name = "reversal_reason") private String reversalReason;
	@Column(name = "create_request_id") private String createRequestId;
	@Version private long version;

	protected PutawayTaskEntity() { }

	PutawayTaskEntity(UUID tenantId, UUID workspaceId, String taskNumber, StockBalanceEntity source,
			StorageLocationEntity target, BigDecimal quantity, UUID actorId, String actorUsername, String requestId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.taskNumber = taskNumber; this.status = "OPEN"; this.sourceBalanceId = source.getId();
		this.sourceWarehouseId = source.getWarehouseId(); this.sourceWarehouseCode = source.getWarehouseCode();
		this.sourceWarehouseName = source.getWarehouseName(); this.sourceLocationId = source.getLocationId();
		this.sourceLocationCode = source.getLocationCode(); this.sourceLocationName = source.getLocationName();
		this.targetLocationId = target.getId(); this.targetLocationCode = target.getCode(); this.targetLocationName = target.getName();
		this.materialId = source.getMaterialId(); this.materialCode = source.getMaterialCode(); this.materialName = source.getMaterialName();
		this.materialSpecification = source.getMaterialSpecification(); this.lotNumber = source.getLotNumber();
		this.unit = source.getUnit(); this.qualityStatus = source.getQualityStatus(); this.quantity = quantity;
		this.createdBy = actorId; this.createdByUsername = actorUsername; this.createdAt = Instant.now(); this.createRequestId = requestId;
	}

	void complete(UUID targetBalanceId, StockMovementEntity sourceOut, StockMovementEntity targetIn, UUID actor, String username) {
		status = "COMPLETED"; this.targetBalanceId = targetBalanceId; sourceOutMovementId = sourceOut.getId();
		sourceOutMovementNumber = sourceOut.getMovementNumber(); targetInMovementId = targetIn.getId();
		targetInMovementNumber = targetIn.getMovementNumber(); completedBy = actor; completedByUsername = username; completedAt = Instant.now();
	}
	void cancel(UUID actor, String username, String reason) {
		status = "CANCELLED"; cancelledBy = actor; cancelledByUsername = username; cancelledAt = Instant.now(); cancellationReason = reason;
	}
	void reverse(StockMovementEntity reverseOut, StockMovementEntity reverseIn, UUID actor, String username, String reason) {
		status = "REVERSED"; reverseOutMovementId = reverseOut.getId(); reverseOutMovementNumber = reverseOut.getMovementNumber();
		reverseInMovementId = reverseIn.getId(); reverseInMovementNumber = reverseIn.getMovementNumber();
		reversedBy = actor; reversedByUsername = username; reversedAt = Instant.now(); reversalReason = reason;
	}

	UUID getId(){return id;} UUID getTenantOrganizationId(){return tenantOrganizationId;} UUID getWorkspaceId(){return workspaceId;}
	String getTaskNumber(){return taskNumber;} String getStatus(){return status;} UUID getSourceBalanceId(){return sourceBalanceId;}
	UUID getSourceWarehouseId(){return sourceWarehouseId;} String getSourceWarehouseCode(){return sourceWarehouseCode;}
	String getSourceWarehouseName(){return sourceWarehouseName;} UUID getSourceLocationId(){return sourceLocationId;}
	String getSourceLocationCode(){return sourceLocationCode;} String getSourceLocationName(){return sourceLocationName;}
	UUID getTargetLocationId(){return targetLocationId;} String getTargetLocationCode(){return targetLocationCode;}
	String getTargetLocationName(){return targetLocationName;} UUID getTargetBalanceId(){return targetBalanceId;}
	UUID getMaterialId(){return materialId;} String getMaterialCode(){return materialCode;} String getMaterialName(){return materialName;}
	String getMaterialSpecification(){return materialSpecification;} String getLotNumber(){return lotNumber;} String getUnit(){return unit;}
	String getQualityStatus(){return qualityStatus;} BigDecimal getQuantity(){return quantity;} long getVersion(){return version;}
	UUID getSourceOutMovementId(){return sourceOutMovementId;} String getSourceOutMovementNumber(){return sourceOutMovementNumber;}
	UUID getTargetInMovementId(){return targetInMovementId;} String getTargetInMovementNumber(){return targetInMovementNumber;}
	UUID getReverseOutMovementId(){return reverseOutMovementId;} String getReverseOutMovementNumber(){return reverseOutMovementNumber;}
	UUID getReverseInMovementId(){return reverseInMovementId;} String getReverseInMovementNumber(){return reverseInMovementNumber;}
	String getCreatedByUsername(){return createdByUsername;} Instant getCreatedAt(){return createdAt;}
	String getCompletedByUsername(){return completedByUsername;} Instant getCompletedAt(){return completedAt;}
	String getCancelledByUsername(){return cancelledByUsername;} Instant getCancelledAt(){return cancelledAt;}
	String getCancellationReason(){return cancellationReason;} String getReversedByUsername(){return reversedByUsername;}
	Instant getReversedAt(){return reversedAt;} String getReversalReason(){return reversalReason;} String getCreateRequestId(){return createRequestId;}
}

@Entity
@Table(schema = "warehouse", name = "putaway_events")
class PutawayEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "task_id") private UUID taskId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "actor_username") private String actorUsername;
	@Column(name = "request_id") private String requestId;
	@Column(name = "occurred_at") private Instant occurredAt;
	protected PutawayEventEntity() { }
	PutawayEventEntity(UUID tenant, UUID workspace, UUID task, String action, String from, String to, String reason,
			UUID actor, String username, String requestId) {
		id=UUID.randomUUID(); tenantOrganizationId=tenant; workspaceId=workspace; taskId=task; this.action=action;
		fromStatus=from; toStatus=to; this.reason=reason; actorUserId=actor; actorUsername=username;
		this.requestId=requestId; occurredAt=Instant.now();
	}
	UUID getTaskId(){return taskId;} String getRequestId(){return requestId;}
}

