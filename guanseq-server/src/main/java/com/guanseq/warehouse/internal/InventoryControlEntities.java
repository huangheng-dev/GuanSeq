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
@Table(schema="warehouse",name="transfer_tasks")
class TransferTaskEntity {
    @Id private UUID id;
    @Column(name="tenant_organization_id") private UUID tenantOrganizationId;
    @Column(name="workspace_id") private UUID workspaceId;
    @Column(name="task_number") private String taskNumber;
    private String status;
    @Column(name="source_balance_id") private UUID sourceBalanceId;
    @Column(name="source_warehouse_id") private UUID sourceWarehouseId;
    @Column(name="source_warehouse_code") private String sourceWarehouseCode;
    @Column(name="source_warehouse_name") private String sourceWarehouseName;
    @Column(name="source_location_id") private UUID sourceLocationId;
    @Column(name="source_location_code") private String sourceLocationCode;
    @Column(name="source_location_name") private String sourceLocationName;
    @Column(name="target_location_id") private UUID targetLocationId;
    @Column(name="target_location_code") private String targetLocationCode;
    @Column(name="target_location_name") private String targetLocationName;
    @Column(name="target_balance_id") private UUID targetBalanceId;
    @Column(name="material_id") private UUID materialId;
    @Column(name="material_code") private String materialCode;
    @Column(name="material_name") private String materialName;
    @Column(name="material_specification") private String materialSpecification;
    @Column(name="lot_number") private String lotNumber;
    private String unit;
    @Column(name="quality_status") private String qualityStatus;
    private BigDecimal quantity;
    @Column(name="transfer_reason") private String transferReason;
    @Column(name="source_out_movement_id") private UUID sourceOutMovementId;
    @Column(name="source_out_movement_number") private String sourceOutMovementNumber;
    @Column(name="target_in_movement_id") private UUID targetInMovementId;
    @Column(name="target_in_movement_number") private String targetInMovementNumber;
    @Column(name="reverse_out_movement_id") private UUID reverseOutMovementId;
    @Column(name="reverse_out_movement_number") private String reverseOutMovementNumber;
    @Column(name="reverse_in_movement_id") private UUID reverseInMovementId;
    @Column(name="reverse_in_movement_number") private String reverseInMovementNumber;
    @Column(name="created_by") private UUID createdBy;
    @Column(name="created_by_username") private String createdByUsername;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="completed_by") private UUID completedBy;
    @Column(name="completed_by_username") private String completedByUsername;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="cancelled_by") private UUID cancelledBy;
    @Column(name="cancelled_by_username") private String cancelledByUsername;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="cancellation_reason") private String cancellationReason;
    @Column(name="reversed_by") private UUID reversedBy;
    @Column(name="reversed_by_username") private String reversedByUsername;
    @Column(name="reversed_at") private Instant reversedAt;
    @Column(name="reversal_reason") private String reversalReason;
    @Column(name="create_request_id") private String createRequestId;
    @Version private long version;

    protected TransferTaskEntity() { }
    TransferTaskEntity(UUID tenant,UUID workspace,String number,StockBalanceEntity source,StorageLocationEntity target,
            BigDecimal quantity,String reason,UUID actor,String username,String requestId){
        id=UUID.randomUUID();tenantOrganizationId=tenant;workspaceId=workspace;taskNumber=number;status="OPEN";
        sourceBalanceId=source.getId();sourceWarehouseId=source.getWarehouseId();sourceWarehouseCode=source.getWarehouseCode();
        sourceWarehouseName=source.getWarehouseName();sourceLocationId=source.getLocationId();sourceLocationCode=source.getLocationCode();
        sourceLocationName=source.getLocationName();targetLocationId=target.getId();targetLocationCode=target.getCode();
        targetLocationName=target.getName();materialId=source.getMaterialId();materialCode=source.getMaterialCode();
        materialName=source.getMaterialName();materialSpecification=source.getMaterialSpecification();lotNumber=source.getLotNumber();
        unit=source.getUnit();qualityStatus=source.getQualityStatus();this.quantity=quantity;transferReason=reason;
        createdBy=actor;createdByUsername=username;createdAt=Instant.now();createRequestId=requestId;
    }
    void complete(UUID targetBalance,StockMovementEntity out,StockMovementEntity in,UUID actor,String username){
        status="COMPLETED";targetBalanceId=targetBalance;sourceOutMovementId=out.getId();sourceOutMovementNumber=out.getMovementNumber();
        targetInMovementId=in.getId();targetInMovementNumber=in.getMovementNumber();completedBy=actor;completedByUsername=username;completedAt=Instant.now();
    }
    void cancel(UUID actor,String username,String reason){status="CANCELLED";cancelledBy=actor;cancelledByUsername=username;cancelledAt=Instant.now();cancellationReason=reason;}
    void reverse(StockMovementEntity out,StockMovementEntity in,UUID actor,String username,String reason){
        status="REVERSED";reverseOutMovementId=out.getId();reverseOutMovementNumber=out.getMovementNumber();
        reverseInMovementId=in.getId();reverseInMovementNumber=in.getMovementNumber();reversedBy=actor;reversedByUsername=username;reversedAt=Instant.now();reversalReason=reason;
    }
    UUID getId(){return id;} UUID getTenantOrganizationId(){return tenantOrganizationId;} UUID getWorkspaceId(){return workspaceId;}
    String getTaskNumber(){return taskNumber;} String getStatus(){return status;} UUID getSourceBalanceId(){return sourceBalanceId;}
    UUID getSourceWarehouseId(){return sourceWarehouseId;} String getSourceWarehouseCode(){return sourceWarehouseCode;} String getSourceWarehouseName(){return sourceWarehouseName;}
    UUID getSourceLocationId(){return sourceLocationId;} String getSourceLocationCode(){return sourceLocationCode;} String getSourceLocationName(){return sourceLocationName;}
    UUID getTargetLocationId(){return targetLocationId;} String getTargetLocationCode(){return targetLocationCode;} String getTargetLocationName(){return targetLocationName;}
    UUID getTargetBalanceId(){return targetBalanceId;} UUID getMaterialId(){return materialId;} String getMaterialCode(){return materialCode;}
    String getMaterialName(){return materialName;} String getMaterialSpecification(){return materialSpecification;} String getLotNumber(){return lotNumber;}
    String getUnit(){return unit;} String getQualityStatus(){return qualityStatus;} BigDecimal getQuantity(){return quantity;} String getTransferReason(){return transferReason;}
    UUID getSourceOutMovementId(){return sourceOutMovementId;} String getSourceOutMovementNumber(){return sourceOutMovementNumber;}
    UUID getTargetInMovementId(){return targetInMovementId;} String getTargetInMovementNumber(){return targetInMovementNumber;}
    UUID getReverseOutMovementId(){return reverseOutMovementId;} String getReverseOutMovementNumber(){return reverseOutMovementNumber;}
    UUID getReverseInMovementId(){return reverseInMovementId;} String getReverseInMovementNumber(){return reverseInMovementNumber;}
    String getCreatedByUsername(){return createdByUsername;} Instant getCreatedAt(){return createdAt;} String getCompletedByUsername(){return completedByUsername;}
    Instant getCompletedAt(){return completedAt;} String getCancelledByUsername(){return cancelledByUsername;} Instant getCancelledAt(){return cancelledAt;}
    String getCancellationReason(){return cancellationReason;} String getReversedByUsername(){return reversedByUsername;} Instant getReversedAt(){return reversedAt;}
    String getReversalReason(){return reversalReason;} String getCreateRequestId(){return createRequestId;} long getVersion(){return version;}
}

@Entity
@Table(schema="warehouse",name="transfer_events")
class TransferEventEntity {
    @Id private UUID id;
    @Column(name="tenant_organization_id") private UUID tenantOrganizationId;
    @Column(name="workspace_id") private UUID workspaceId;
    @Column(name="task_id") private UUID taskId;
    private String action;
    @Column(name="from_status") private String fromStatus;
    @Column(name="to_status") private String toStatus;
    private String reason;
    @Column(name="actor_user_id") private UUID actorUserId;
    @Column(name="actor_username") private String actorUsername;
    @Column(name="request_id") private String requestId;
    @Column(name="occurred_at") private Instant occurredAt;
    protected TransferEventEntity() { }
    TransferEventEntity(UUID tenant,UUID workspace,UUID task,String action,String from,String to,String reason,UUID actor,String username,String request){
        id=UUID.randomUUID();tenantOrganizationId=tenant;workspaceId=workspace;taskId=task;this.action=action;fromStatus=from;toStatus=to;
        this.reason=reason;actorUserId=actor;actorUsername=username;requestId=request;occurredAt=Instant.now();
    }
    UUID getTaskId(){return taskId;}
}

@Entity
@Table(schema="warehouse",name="stock_count_tasks")
class StockCountTaskEntity {
    @Id private UUID id;
    @Column(name="tenant_organization_id") private UUID tenantOrganizationId;
    @Column(name="workspace_id") private UUID workspaceId;
    @Column(name="count_number") private String countNumber;
    private String status;
    @Column(name="balance_id") private UUID balanceId;
    @Column(name="warehouse_id") private UUID warehouseId;
    @Column(name="warehouse_code") private String warehouseCode;
    @Column(name="warehouse_name") private String warehouseName;
    @Column(name="location_id") private UUID locationId;
    @Column(name="location_code") private String locationCode;
    @Column(name="location_name") private String locationName;
    @Column(name="material_id") private UUID materialId;
    @Column(name="material_code") private String materialCode;
    @Column(name="material_name") private String materialName;
    @Column(name="material_specification") private String materialSpecification;
    @Column(name="lot_number") private String lotNumber;
    private String unit;
    @Column(name="quality_status") private String qualityStatus;
    @Column(name="book_on_hand") private BigDecimal bookOnHand;
    @Column(name="book_allocated") private BigDecimal bookAllocated;
    @Column(name="book_frozen") private BigDecimal bookFrozen;
    @Column(name="counted_quantity") private BigDecimal countedQuantity;
    @Column(name="difference_quantity") private BigDecimal differenceQuantity;
    @Column(name="snapshot_balance_version") private long snapshotBalanceVersion;
    @Column(name="adjustment_movement_id") private UUID adjustmentMovementId;
    @Column(name="adjustment_movement_number") private String adjustmentMovementNumber;
    @Column(name="adjustment_movement_type") private String adjustmentMovementType;
    @Column(name="reverse_movement_id") private UUID reverseMovementId;
    @Column(name="reverse_movement_number") private String reverseMovementNumber;
    @Column(name="reverse_movement_type") private String reverseMovementType;
    @Column(name="count_note") private String countNote;
    @Column(name="approval_comment") private String approvalComment;
    @Column(name="created_by") private UUID createdBy;
    @Column(name="created_by_username") private String createdByUsername;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="counted_by") private UUID countedBy;
    @Column(name="counted_by_username") private String countedByUsername;
    @Column(name="counted_at") private Instant countedAt;
    @Column(name="approved_by") private UUID approvedBy;
    @Column(name="approved_by_username") private String approvedByUsername;
    @Column(name="approved_at") private Instant approvedAt;
    @Column(name="cancelled_by") private UUID cancelledBy;
    @Column(name="cancelled_by_username") private String cancelledByUsername;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="cancellation_reason") private String cancellationReason;
    @Column(name="reversed_by") private UUID reversedBy;
    @Column(name="reversed_by_username") private String reversedByUsername;
    @Column(name="reversed_at") private Instant reversedAt;
    @Column(name="reversal_reason") private String reversalReason;
    @Column(name="create_request_id") private String createRequestId;
    @Version private long version;

    protected StockCountTaskEntity() { }
    StockCountTaskEntity(UUID tenant,UUID workspace,String number,StockBalanceEntity balance,UUID actor,String username,String requestId){
        id=UUID.randomUUID();tenantOrganizationId=tenant;workspaceId=workspace;countNumber=number;status="OPEN";balanceId=balance.getId();
        warehouseId=balance.getWarehouseId();warehouseCode=balance.getWarehouseCode();warehouseName=balance.getWarehouseName();
        locationId=balance.getLocationId();locationCode=balance.getLocationCode();locationName=balance.getLocationName();materialId=balance.getMaterialId();
        materialCode=balance.getMaterialCode();materialName=balance.getMaterialName();materialSpecification=balance.getMaterialSpecification();
        lotNumber=balance.getLotNumber();unit=balance.getUnit();qualityStatus=balance.getQualityStatus();bookOnHand=balance.getOnHandQuantity();
        bookAllocated=balance.getAllocatedQuantity();bookFrozen=balance.getFrozenQuantity();snapshotBalanceVersion=balance.getVersion();
        createdBy=actor;createdByUsername=username;createdAt=Instant.now();createRequestId=requestId;
    }
    void recordCount(BigDecimal counted,String note,UUID actor,String username){
        status="COUNTED";countedQuantity=counted;differenceQuantity=counted.subtract(bookOnHand);countNote=note;
        countedBy=actor;countedByUsername=username;countedAt=Instant.now();
    }
    void approve(StockMovementEntity movement,String comment,UUID actor,String username){
        status="APPROVED";approvalComment=comment;if(movement!=null){adjustmentMovementId=movement.getId();adjustmentMovementNumber=movement.getMovementNumber();adjustmentMovementType=movement.getMovementType();}
        approvedBy=actor;approvedByUsername=username;approvedAt=Instant.now();
    }
    void cancel(UUID actor,String username,String reason){status="CANCELLED";cancelledBy=actor;cancelledByUsername=username;cancelledAt=Instant.now();cancellationReason=reason;}
    void reverse(StockMovementEntity movement,UUID actor,String username,String reason){
        status="REVERSED";reverseMovementId=movement.getId();reverseMovementNumber=movement.getMovementNumber();reverseMovementType=movement.getMovementType();
        reversedBy=actor;reversedByUsername=username;reversedAt=Instant.now();reversalReason=reason;
    }
    UUID getId(){return id;} UUID getTenantOrganizationId(){return tenantOrganizationId;} UUID getWorkspaceId(){return workspaceId;}
    String getCountNumber(){return countNumber;} String getStatus(){return status;} UUID getBalanceId(){return balanceId;} UUID getWarehouseId(){return warehouseId;}
    String getWarehouseCode(){return warehouseCode;} String getWarehouseName(){return warehouseName;} UUID getLocationId(){return locationId;}
    String getLocationCode(){return locationCode;} String getLocationName(){return locationName;} UUID getMaterialId(){return materialId;}
    String getMaterialCode(){return materialCode;} String getMaterialName(){return materialName;} String getMaterialSpecification(){return materialSpecification;}
    String getLotNumber(){return lotNumber;} String getUnit(){return unit;} String getQualityStatus(){return qualityStatus;} BigDecimal getBookOnHand(){return bookOnHand;}
    BigDecimal getBookAllocated(){return bookAllocated;} BigDecimal getBookFrozen(){return bookFrozen;} BigDecimal getCountedQuantity(){return countedQuantity;}
    BigDecimal getDifferenceQuantity(){return differenceQuantity;} long getSnapshotBalanceVersion(){return snapshotBalanceVersion;}
    UUID getAdjustmentMovementId(){return adjustmentMovementId;} String getAdjustmentMovementNumber(){return adjustmentMovementNumber;}
    String getAdjustmentMovementType(){return adjustmentMovementType;} UUID getReverseMovementId(){return reverseMovementId;}
    String getReverseMovementNumber(){return reverseMovementNumber;} String getReverseMovementType(){return reverseMovementType;}
    String getCountNote(){return countNote;} String getApprovalComment(){return approvalComment;} String getCreatedByUsername(){return createdByUsername;}
    Instant getCreatedAt(){return createdAt;} String getCountedByUsername(){return countedByUsername;} Instant getCountedAt(){return countedAt;}
    String getApprovedByUsername(){return approvedByUsername;} Instant getApprovedAt(){return approvedAt;} String getCancelledByUsername(){return cancelledByUsername;}
    Instant getCancelledAt(){return cancelledAt;} String getCancellationReason(){return cancellationReason;} String getReversedByUsername(){return reversedByUsername;}
    Instant getReversedAt(){return reversedAt;} String getReversalReason(){return reversalReason;} String getCreateRequestId(){return createRequestId;} long getVersion(){return version;}
}

@Entity
@Table(schema="warehouse",name="stock_count_events")
class StockCountEventEntity {
    @Id private UUID id;
    @Column(name="tenant_organization_id") private UUID tenantOrganizationId;
    @Column(name="workspace_id") private UUID workspaceId;
    @Column(name="task_id") private UUID taskId;
    private String action;
    @Column(name="from_status") private String fromStatus;
    @Column(name="to_status") private String toStatus;
    private String reason;
    @Column(name="actor_user_id") private UUID actorUserId;
    @Column(name="actor_username") private String actorUsername;
    @Column(name="request_id") private String requestId;
    @Column(name="occurred_at") private Instant occurredAt;
    protected StockCountEventEntity() { }
    StockCountEventEntity(UUID tenant,UUID workspace,UUID task,String action,String from,String to,String reason,UUID actor,String username,String request){
        id=UUID.randomUUID();tenantOrganizationId=tenant;workspaceId=workspace;taskId=task;this.action=action;fromStatus=from;toStatus=to;
        this.reason=reason;actorUserId=actor;actorUsername=username;requestId=request;occurredAt=Instant.now();
    }
    UUID getTaskId(){return taskId;}
}
