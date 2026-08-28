package com.guanseq.equipment.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
@Table(schema = "equipment", name = "maintenance_plans")
class EquipmentMaintenancePlanEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "plan_code") private String planCode;
	@Column(name = "creation_request_id") private String creationRequestId;
	private String name;
	@Column(name = "work_type") private String workType;
	@Column(name = "asset_id") private UUID assetId;
	@Column(name = "asset_code_snapshot") private String assetCodeSnapshot;
	@Column(name = "asset_name_snapshot") private String assetNameSnapshot;
	@Column(name = "asset_location_snapshot") private String assetLocationSnapshot;
	private String description;
	private String priority;
	@Column(name = "interval_days") private int intervalDays;
	@Column(name = "lead_days") private int leadDays;
	@Column(name = "first_due_date") private LocalDate firstDueDate;
	@Column(name = "next_due_date") private LocalDate nextDueDate;
	@Column(name = "planned_start_time") private LocalTime plannedStartTime;
	@Column(name = "due_time") private LocalTime dueTime;
	private String assignee;
	private String status;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentMaintenancePlanEntity() { }

	EquipmentMaintenancePlanEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String planCode,
			String creationRequestId, String name, String workType, EquipmentAssetEntity asset, String description,
			String priority, int intervalDays, int leadDays, LocalDate firstDueDate, LocalTime plannedStartTime,
			LocalTime dueTime, String assignee, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId;
		this.planCode = planCode.trim().toUpperCase();
		this.creationRequestId = creationRequestId;
		this.name = name.trim();
		this.workType = workType;
		this.assetId = asset.getId();
		this.assetCodeSnapshot = asset.getAssetCode();
		this.assetNameSnapshot = asset.getAssetName();
		this.assetLocationSnapshot = asset.getLocation();
		this.description = description.trim();
		this.priority = priority;
		this.intervalDays = intervalDays;
		this.leadDays = leadDays;
		this.firstDueDate = firstDueDate;
		this.nextDueDate = firstDueDate;
		this.plannedStartTime = plannedStartTime;
		this.dueTime = dueTime;
		this.assignee = assignee.trim();
		this.status = "ACTIVE";
		this.createdBy = actorId;
		this.createdAt = Instant.now();
		this.updatedBy = actorId;
		this.updatedAt = this.createdAt;
	}

	void changeStatus(String nextStatus, UUID actorId) {
		this.status = nextStatus;
		this.updatedBy = actorId;
		this.updatedAt = Instant.now();
	}

	void advance(UUID actorId) {
		this.nextDueDate = this.nextDueDate.plusDays(intervalDays);
		this.updatedBy = actorId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getPlanCode() { return planCode; }
	String getName() { return name; }
	String getWorkType() { return workType; }
	UUID getAssetId() { return assetId; }
	String getAssetCodeSnapshot() { return assetCodeSnapshot; }
	String getAssetNameSnapshot() { return assetNameSnapshot; }
	String getAssetLocationSnapshot() { return assetLocationSnapshot; }
	String getDescription() { return description; }
	String getPriority() { return priority; }
	int getIntervalDays() { return intervalDays; }
	int getLeadDays() { return leadDays; }
	LocalDate getFirstDueDate() { return firstDueDate; }
	LocalDate getNextDueDate() { return nextDueDate; }
	LocalTime getPlannedStartTime() { return plannedStartTime; }
	LocalTime getDueTime() { return dueTime; }
	String getAssignee() { return assignee; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "equipment", name = "maintenance_plan_events")
class EquipmentMaintenancePlanEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "plan_id") private UUID planId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected EquipmentMaintenancePlanEventEntity() { }

	EquipmentMaintenancePlanEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID planId, String action,
			String fromStatus, String toStatus, String reason, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorId;
		this.planId = planId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason.trim();
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
	String getRequestId() { return requestId; }
	Map<String, Object> getDetails() { return details; }
	Instant getOccurredAt() { return occurredAt; }
}

@Entity
@Table(schema = "equipment", name = "maintenance_generation_runs")
class EquipmentMaintenanceGenerationRunEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "request_id") private String requestId;
	@Column(name = "as_of_date") private LocalDate asOfDate;
	private String reason;
	private String status;
	@Column(name = "generated_count") private int generatedCount;
	@Column(name = "existing_count") private int existingCount;
	@Column(name = "skipped_count") private int skippedCount;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "started_at") private Instant startedAt;
	@Column(name = "completed_at") private Instant completedAt;

	protected EquipmentMaintenanceGenerationRunEntity() { }

	EquipmentMaintenanceGenerationRunEntity(UUID tenantId, UUID workspaceId, String requestId, LocalDate asOfDate,
			String reason, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.workspaceId = workspaceId;
		this.requestId = requestId;
		this.asOfDate = asOfDate;
		this.reason = reason.trim();
		this.status = "RUNNING";
		this.actorUserId = actorId;
		this.startedAt = Instant.now();
	}

	void complete(int generated, int existing, int skipped) {
		this.generatedCount = generated;
		this.existingCount = existing;
		this.skippedCount = skipped;
		this.status = "COMPLETED";
		this.completedAt = Instant.now();
	}

	UUID getId() { return id; }
	String getRequestId() { return requestId; }
	LocalDate getAsOfDate() { return asOfDate; }
	String getReason() { return reason; }
	String getStatus() { return status; }
	int getGeneratedCount() { return generatedCount; }
	int getExistingCount() { return existingCount; }
	int getSkippedCount() { return skippedCount; }
	UUID getActorUserId() { return actorUserId; }
	Instant getStartedAt() { return startedAt; }
	Instant getCompletedAt() { return completedAt; }
}

@Entity
@Table(schema = "equipment", name = "maintenance_generation_items")
class EquipmentMaintenanceGenerationItemEntity {
	@Id private UUID id;
	@Column(name = "run_id") private UUID runId;
	@Column(name = "plan_id") private UUID planId;
	@Column(name = "due_date") private LocalDate dueDate;
	private String outcome;
	@Column(name = "work_order_id") private UUID workOrderId;
	private String message;

	protected EquipmentMaintenanceGenerationItemEntity() { }

	EquipmentMaintenanceGenerationItemEntity(UUID runId, UUID planId, LocalDate dueDate, String outcome,
			UUID workOrderId, String message) {
		this.id = UUID.randomUUID();
		this.runId = runId;
		this.planId = planId;
		this.dueDate = dueDate;
		this.outcome = outcome;
		this.workOrderId = workOrderId;
		this.message = message;
	}

	UUID getId() { return id; }
	UUID getPlanId() { return planId; }
	LocalDate getDueDate() { return dueDate; }
	String getOutcome() { return outcome; }
	UUID getWorkOrderId() { return workOrderId; }
	String getMessage() { return message; }
}
