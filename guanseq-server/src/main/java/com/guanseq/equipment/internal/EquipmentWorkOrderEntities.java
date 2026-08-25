package com.guanseq.equipment.internal;

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

@Entity
@Table(schema = "equipment", name = "maintenance_work_orders")
class EquipmentWorkOrderEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "work_order_number") private String workOrderNumber;
	@Column(name = "creation_request_id") private String creationRequestId;
	@Column(name = "work_type") private String workType;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "source_work_order_id") private UUID sourceWorkOrderId;
	@Column(name = "asset_id") private UUID assetId;
	@Column(name = "asset_code_snapshot") private String assetCodeSnapshot;
	@Column(name = "asset_name_snapshot") private String assetNameSnapshot;
	@Column(name = "asset_location_snapshot") private String assetLocationSnapshot;
	private String title;
	private String description;
	private String priority;
	private String status;
	@Column(name = "planned_start_at") private Instant plannedStartAt;
	@Column(name = "due_at") private Instant dueAt;
	private String assignee;
	private String outcome;
	@Column(name = "completion_notes") private String completionNotes;
	@Column(name = "started_at") private Instant startedAt;
	@Column(name = "submitted_at") private Instant submittedAt;
	@Column(name = "completed_at") private Instant completedAt;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentWorkOrderEntity() { }

	EquipmentWorkOrderEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String number, String creationRequestId, String workType,
			String sourceType, UUID sourceWorkOrderId, EquipmentAssetEntity asset, String title, String description,
			String priority, Instant plannedStartAt, Instant dueAt, String assignee, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId;
		this.workOrderNumber = number;
		this.creationRequestId = creationRequestId;
		this.workType = workType;
		this.sourceType = sourceType;
		this.sourceWorkOrderId = sourceWorkOrderId;
		this.assetId = asset.getId();
		this.assetCodeSnapshot = asset.getAssetCode();
		this.assetNameSnapshot = asset.getAssetName();
		this.assetLocationSnapshot = asset.getLocation();
		this.title = title.trim();
		this.description = description.trim();
		this.priority = priority;
		this.status = "PLANNED";
		this.plannedStartAt = plannedStartAt;
		this.dueAt = dueAt;
		this.assignee = assignee.trim();
		this.createdBy = actorId;
		this.createdAt = Instant.now();
		this.updatedBy = actorId;
		this.updatedAt = this.createdAt;
	}

	void transition(String nextStatus, String nextOutcome, String notes, UUID actorId) {
		Instant now = Instant.now();
		this.status = nextStatus;
		if (nextOutcome != null) this.outcome = nextOutcome;
		if (notes != null && !notes.isBlank()) this.completionNotes = notes.trim();
		if ("IN_PROGRESS".equals(nextStatus) && this.startedAt == null) this.startedAt = now;
		if ("WAITING_ACCEPTANCE".equals(nextStatus)) this.submittedAt = now;
		if ("COMPLETED".equals(nextStatus) || "CANCELLED".equals(nextStatus)) this.completedAt = now;
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	UUID getId() { return id; }
	UUID getAssetId() { return assetId; }
	String getWorkOrderNumber() { return workOrderNumber; }
	String getWorkType() { return workType; }
	String getSourceType() { return sourceType; }
	UUID getSourceWorkOrderId() { return sourceWorkOrderId; }
	String getAssetCodeSnapshot() { return assetCodeSnapshot; }
	String getAssetNameSnapshot() { return assetNameSnapshot; }
	String getAssetLocationSnapshot() { return assetLocationSnapshot; }
	String getTitle() { return title; }
	String getDescription() { return description; }
	String getPriority() { return priority; }
	String getStatus() { return status; }
	Instant getPlannedStartAt() { return plannedStartAt; }
	Instant getDueAt() { return dueAt; }
	String getAssignee() { return assignee; }
	String getOutcome() { return outcome; }
	String getCompletionNotes() { return completionNotes; }
	Instant getStartedAt() { return startedAt; }
	Instant getSubmittedAt() { return submittedAt; }
	Instant getCompletedAt() { return completedAt; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "equipment", name = "maintenance_work_order_events")
class EquipmentWorkOrderEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "work_order_id") private UUID workOrderId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	private String outcome;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected EquipmentWorkOrderEventEntity() { }

	EquipmentWorkOrderEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID workOrderId, String action,
			String fromStatus, String toStatus, String reason, String outcome, String requestId,
			Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorId;
		this.workOrderId = workOrderId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason.trim();
		this.outcome = outcome;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getActorUserId() { return actorUserId; }
	String getAction() { return action; }
	String getFromStatus() { return fromStatus; }
	String getToStatus() { return toStatus; }
	String getReason() { return reason; }
	String getOutcome() { return outcome; }
	String getRequestId() { return requestId; }
	Map<String, Object> getDetails() { return details; }
	Instant getOccurredAt() { return occurredAt; }
}
