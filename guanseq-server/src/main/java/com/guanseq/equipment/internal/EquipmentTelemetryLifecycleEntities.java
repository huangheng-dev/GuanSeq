package com.guanseq.equipment.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "equipment", name = "telemetry_retention_policies")
class EquipmentTelemetryRetentionPolicyEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "retention_days") private int retentionDays;
	@Column(name = "automatic_cleanup_enabled") private boolean automaticCleanupEnabled;
	@Column(name = "cleanup_interval_hours") private int cleanupIntervalHours;
	@Column(name = "next_cleanup_at") private Instant nextCleanupAt;
	@Column(name = "last_automation_status") private String lastAutomationStatus;
	@Column(name = "last_automation_completed_at") private Instant lastAutomationCompletedAt;
	@Column(name = "consecutive_failures") private int consecutiveFailures;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentTelemetryRetentionPolicyEntity() { }

	EquipmentTelemetryRetentionPolicyEntity(UUID tenantOrganizationId, UUID workspaceId,
			int retentionDays, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.retentionDays = retentionDays;
		this.automaticCleanupEnabled = false;
		this.cleanupIntervalHours = 24;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void update(int retentionDays, boolean automaticCleanupEnabled, int cleanupIntervalHours,
			UUID actorUserId) {
		this.retentionDays = retentionDays;
		this.automaticCleanupEnabled = automaticCleanupEnabled;
		this.cleanupIntervalHours = cleanupIntervalHours;
		this.nextCleanupAt = automaticCleanupEnabled
				? Instant.now().plus(cleanupIntervalHours, ChronoUnit.HOURS) : null;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void automationSucceeded(String status, Instant completedAt, Instant nextCleanupAt) {
		this.lastAutomationStatus = status;
		this.lastAutomationCompletedAt = completedAt;
		this.consecutiveFailures = 0;
		this.nextCleanupAt = nextCleanupAt;
	}

	void automationFailed(Instant completedAt, Instant nextCleanupAt) {
		this.lastAutomationStatus = "FAILED";
		this.lastAutomationCompletedAt = completedAt;
		this.consecutiveFailures += 1;
		this.nextCleanupAt = nextCleanupAt;
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	int getRetentionDays() { return retentionDays; }
	boolean isAutomaticCleanupEnabled() { return automaticCleanupEnabled; }
	int getCleanupIntervalHours() { return cleanupIntervalHours; }
	Instant getNextCleanupAt() { return nextCleanupAt; }
	String getLastAutomationStatus() { return lastAutomationStatus; }
	Instant getLastAutomationCompletedAt() { return lastAutomationCompletedAt; }
	int getConsecutiveFailures() { return consecutiveFailures; }
	long getVersion() { return version; }
	UUID getUpdatedBy() { return updatedBy; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "equipment", name = "telemetry_retention_runs")
class EquipmentTelemetryRetentionRunEntity {
	@Id private UUID id;
	@Column(name = "policy_id") private UUID policyId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "trigger_type") private String triggerType;
	private String status;
	@Column(name = "initiated_by") private UUID initiatedBy;
	@Column(name = "instance_id") private String instanceId;
	@Column(name = "request_id") private String requestId;
	private String reason;
	@Column(name = "cutoff_at") private Instant cutoffAt;
	@Column(name = "deleted_sample_count") private long deletedSampleCount;
	@Column(name = "remaining_expired_count") private long remainingExpiredCount;
	@Column(name = "failure_code") private String failureCode;
	@Column(name = "failure_summary") private String failureSummary;
	@Column(name = "attention_status") private String attentionStatus;
	@Column(name = "acknowledgement_request_id") private String acknowledgementRequestId;
	@Column(name = "acknowledged_by") private UUID acknowledgedBy;
	@Column(name = "acknowledged_at") private Instant acknowledgedAt;
	@Column(name = "acknowledgement_note") private String acknowledgementNote;
	@Column(name = "started_at") private Instant startedAt;
	@Column(name = "completed_at") private Instant completedAt;

	protected EquipmentTelemetryRetentionRunEntity() { }

	static EquipmentTelemetryRetentionRunEntity completed(EquipmentTelemetryRetentionPolicyEntity policy,
			String triggerType, UUID initiatedBy, String instanceId, String requestId, String reason, Instant cutoffAt,
			long deletedSampleCount, long remainingExpiredCount, Instant startedAt, Instant completedAt) {
		EquipmentTelemetryRetentionRunEntity run = base(policy, triggerType, initiatedBy, instanceId,
				requestId, reason, cutoffAt, startedAt, completedAt);
		run.status = remainingExpiredCount > 0 ? "PARTIAL" : "SUCCEEDED";
		run.deletedSampleCount = deletedSampleCount;
		run.remainingExpiredCount = remainingExpiredCount;
		run.attentionStatus = "NONE";
		return run;
	}

	static EquipmentTelemetryRetentionRunEntity failed(EquipmentTelemetryRetentionPolicyEntity policy,
			String triggerType, UUID initiatedBy, String instanceId, String requestId, String reason, Instant cutoffAt,
			String failureCode, String failureSummary, Instant startedAt, Instant completedAt) {
		EquipmentTelemetryRetentionRunEntity run = base(policy, triggerType, initiatedBy, instanceId,
				requestId, reason, cutoffAt, startedAt, completedAt);
		run.status = "FAILED";
		run.failureCode = failureCode;
		run.failureSummary = failureSummary;
		run.attentionStatus = "OPEN";
		return run;
	}

	private static EquipmentTelemetryRetentionRunEntity base(EquipmentTelemetryRetentionPolicyEntity policy,
			String triggerType, UUID initiatedBy, String instanceId, String requestId, String reason, Instant cutoffAt,
			Instant startedAt, Instant completedAt) {
		EquipmentTelemetryRetentionRunEntity run = new EquipmentTelemetryRetentionRunEntity();
		run.id = UUID.randomUUID();
		run.policyId = policy.getId();
		run.tenantOrganizationId = policy.getTenantOrganizationId();
		run.workspaceId = policy.getWorkspaceId();
		run.triggerType = triggerType;
		run.initiatedBy = initiatedBy;
		run.instanceId = instanceId;
		run.requestId = requestId;
		run.reason = reason.trim();
		run.cutoffAt = cutoffAt;
		run.startedAt = startedAt;
		run.completedAt = completedAt;
		return run;
	}

	void acknowledge(UUID actorUserId, String requestId, String note) {
		this.attentionStatus = "ACKNOWLEDGED";
		this.acknowledgementRequestId = requestId;
		this.acknowledgedBy = actorUserId;
		this.acknowledgedAt = Instant.now();
		this.acknowledgementNote = note.trim();
	}

	UUID getId() { return id; }
	String getTriggerType() { return triggerType; }
	String getStatus() { return status; }
	UUID getInitiatedBy() { return initiatedBy; }
	String getInstanceId() { return instanceId; }
	String getRequestId() { return requestId; }
	String getReason() { return reason; }
	Instant getCutoffAt() { return cutoffAt; }
	long getDeletedSampleCount() { return deletedSampleCount; }
	long getRemainingExpiredCount() { return remainingExpiredCount; }
	String getFailureCode() { return failureCode; }
	String getFailureSummary() { return failureSummary; }
	String getAttentionStatus() { return attentionStatus; }
	String getAcknowledgementRequestId() { return acknowledgementRequestId; }
	UUID getAcknowledgedBy() { return acknowledgedBy; }
	Instant getAcknowledgedAt() { return acknowledgedAt; }
	String getAcknowledgementNote() { return acknowledgementNote; }
	Instant getStartedAt() { return startedAt; }
	Instant getCompletedAt() { return completedAt; }
}

@Entity
@Table(schema = "equipment", name = "telemetry_retention_events")
class EquipmentTelemetryRetentionEventEntity {
	@Id private UUID id;
	@Column(name = "policy_id") private UUID policyId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	private String action;
	@Column(name = "from_retention_days") private int fromRetentionDays;
	@Column(name = "to_retention_days") private int toRetentionDays;
	@Column(name = "from_automatic_cleanup_enabled") private Boolean fromAutomaticCleanupEnabled;
	@Column(name = "to_automatic_cleanup_enabled") private Boolean toAutomaticCleanupEnabled;
	@Column(name = "from_cleanup_interval_hours") private Integer fromCleanupIntervalHours;
	@Column(name = "to_cleanup_interval_hours") private Integer toCleanupIntervalHours;
	@Column(name = "cutoff_at") private Instant cutoffAt;
	@Column(name = "deleted_sample_count") private long deletedSampleCount;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected EquipmentTelemetryRetentionEventEntity() { }

	EquipmentTelemetryRetentionEventEntity(EquipmentTelemetryRetentionPolicyEntity policy,
			UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, String action,
			int fromRetentionDays, int toRetentionDays, Instant cutoffAt, long deletedSampleCount,
			String reason, String requestId, Boolean fromAutomaticCleanupEnabled,
			Boolean toAutomaticCleanupEnabled, Integer fromCleanupIntervalHours,
			Integer toCleanupIntervalHours) {
		this.id = UUID.randomUUID();
		this.policyId = policy.getId();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.action = action;
		this.fromRetentionDays = fromRetentionDays;
		this.toRetentionDays = toRetentionDays;
		this.fromAutomaticCleanupEnabled = fromAutomaticCleanupEnabled;
		this.toAutomaticCleanupEnabled = toAutomaticCleanupEnabled;
		this.fromCleanupIntervalHours = fromCleanupIntervalHours;
		this.toCleanupIntervalHours = toCleanupIntervalHours;
		this.cutoffAt = cutoffAt;
		this.deletedSampleCount = deletedSampleCount;
		this.reason = reason.trim();
		this.requestId = requestId;
		this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getActorUserId() { return actorUserId; }
	String getAction() { return action; }
	int getFromRetentionDays() { return fromRetentionDays; }
	int getToRetentionDays() { return toRetentionDays; }
	Boolean getFromAutomaticCleanupEnabled() { return fromAutomaticCleanupEnabled; }
	Boolean getToAutomaticCleanupEnabled() { return toAutomaticCleanupEnabled; }
	Integer getFromCleanupIntervalHours() { return fromCleanupIntervalHours; }
	Integer getToCleanupIntervalHours() { return toCleanupIntervalHours; }
	Instant getCutoffAt() { return cutoffAt; }
	long getDeletedSampleCount() { return deletedSampleCount; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	Instant getOccurredAt() { return occurredAt; }
}
