package com.guanseq.production.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "production", name = "operation_labor_entries")
class OperationLaborEntryEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "entry_number") private String entryNumber;
	@Column(name = "task_id") private UUID taskId;
	@Column(name = "task_number") private String taskNumber;
	@Column(name = "order_id") private UUID orderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "operation_code") private String operationCode;
	@Column(name = "operation_name") private String operationName;
	@Column(name = "work_center_code") private String workCenterCode;
	@Column(name = "work_center_name") private String workCenterName;
	@Column(name = "work_date") private LocalDate workDate;
	@Column(name = "shift_name") private String shiftName;
	@Column(name = "operator_name") private String operatorName;
	@Column(name = "actual_minutes") private BigDecimal actualMinutes;
	private String status;
	private String note;
	@Column(name = "request_id") private String requestId;
	@Column(name = "approved_by") private UUID approvedBy;
	@Column(name = "approved_at") private Instant approvedAt;
	@Column(name = "approve_request_id") private String approveRequestId;
	@Column(name = "voided_by") private UUID voidedBy;
	@Column(name = "voided_at") private Instant voidedAt;
	@Column(name = "void_reason") private String voidReason;
	@Column(name = "void_request_id") private String voidRequestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected OperationLaborEntryEntity() { }

	OperationLaborEntryEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String entryNumber,
			OperationTaskEntity task, LocalDate workDate, String shiftName, String operatorName,
			BigDecimal actualMinutes, String note, String requestId, UUID actorId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId; this.entryNumber = entryNumber; this.taskId = task.getId();
		this.taskNumber = task.getTaskNumber(); this.orderId = task.getOrderId(); this.orderNumber = task.getOrderNumber();
		this.operationCode = task.getOperationCode(); this.operationName = task.getOperationName();
		this.workCenterCode = task.getWorkCenterCode(); this.workCenterName = task.getWorkCenterName();
		this.workDate = workDate; this.shiftName = shiftName; this.operatorName = operatorName;
		this.actualMinutes = actualMinutes; this.status = "RECORDED"; this.note = note; this.requestId = requestId;
		this.createdBy = actorId; this.createdAt = Instant.now(); this.updatedBy = actorId; this.updatedAt = createdAt;
	}

	void approve(String requestId, UUID actorId) {
		if (!"RECORDED".equals(status)) throw new IllegalStateException("只有已登记工时可以审核");
		this.status = "APPROVED"; this.approvedBy = actorId; this.approvedAt = Instant.now();
		this.approveRequestId = requestId; this.updatedBy = actorId; this.updatedAt = approvedAt;
	}

	void voidEntry(String reason, String requestId, UUID actorId) {
		if (!"RECORDED".equals(status) && !"APPROVED".equals(status))
			throw new IllegalStateException("只有已登记或已审核工时可以冲销");
		this.status = "VOIDED"; this.voidedBy = actorId; this.voidedAt = Instant.now();
		this.voidReason = reason; this.voidRequestId = requestId; this.updatedBy = actorId; this.updatedAt = voidedAt;
	}

	UUID getId() { return id; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; } UUID getWorkspaceId() { return workspaceId; }
	String getEntryNumber() { return entryNumber; } UUID getTaskId() { return taskId; } String getTaskNumber() { return taskNumber; }
	UUID getOrderId() { return orderId; } String getOrderNumber() { return orderNumber; }
	String getOperationCode() { return operationCode; } String getOperationName() { return operationName; }
	String getWorkCenterCode() { return workCenterCode; } String getWorkCenterName() { return workCenterName; }
	LocalDate getWorkDate() { return workDate; } String getShiftName() { return shiftName; } String getOperatorName() { return operatorName; }
	BigDecimal getActualMinutes() { return actualMinutes; } String getStatus() { return status; } String getNote() { return note; }
	String getRequestId() { return requestId; } UUID getApprovedBy() { return approvedBy; } Instant getApprovedAt() { return approvedAt; }
	String getApproveRequestId() { return approveRequestId; } UUID getVoidedBy() { return voidedBy; } Instant getVoidedAt() { return voidedAt; }
	String getVoidReason() { return voidReason; } String getVoidRequestId() { return voidRequestId; }
	long getVersion() { return version; } UUID getCreatedBy() { return createdBy; } Instant getCreatedAt() { return createdAt; }
	UUID getUpdatedBy() { return updatedBy; } Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "production", name = "operation_labor_events")
class OperationLaborEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "entry_id") private UUID entryId;
	@Column(name = "task_id") private UUID taskId;
	@Column(name = "order_id") private UUID orderId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	private String comment;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected OperationLaborEventEntity() { }

	OperationLaborEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID entryId, UUID taskId,
			UUID orderId, String action, String fromStatus, String toStatus, String requestId,
			String comment, Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.actorUserId = actorId; this.entryId = entryId; this.taskId = taskId; this.orderId = orderId;
		this.action = action; this.fromStatus = fromStatus; this.toStatus = toStatus; this.requestId = requestId;
		this.comment = comment; this.details = details == null ? Map.of() : details; this.occurredAt = Instant.now();
	}

	UUID getId() { return id; } UUID getEntryId() { return entryId; } String getAction() { return action; }
	String getFromStatus() { return fromStatus; } String getToStatus() { return toStatus; }
	String getRequestId() { return requestId; } String getComment() { return comment; } Instant getOccurredAt() { return occurredAt; }
}
