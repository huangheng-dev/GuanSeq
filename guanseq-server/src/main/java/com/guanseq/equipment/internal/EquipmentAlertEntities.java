package com.guanseq.equipment.internal;

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

@Entity
@Table(schema = "equipment", name = "alert_rules")
class EquipmentAlertRuleEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "rule_code") private String ruleCode;
	private String name;
	@Column(name = "connection_id") private UUID connectionId;
	@Column(name = "point_id") private UUID pointId;
	@Column(name = "rule_type") private String ruleType;
	@Column(name = "threshold_value") private BigDecimal thresholdValue;
	private String severity;
	@Column(name = "default_assignee") private String defaultAssignee;
	private String status;
	@Version private long version;
	@Column(name = "creation_request_id") private String creationRequestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentAlertRuleEntity() { }

	EquipmentAlertRuleEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String ruleCode, String name,
			UUID connectionId, UUID pointId, String ruleType, BigDecimal thresholdValue, String severity,
			String defaultAssignee, String creationRequestId, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId;
		this.ruleCode = ruleCode.trim().toUpperCase();
		this.name = name.trim();
		this.connectionId = connectionId;
		this.pointId = pointId;
		this.ruleType = ruleType;
		this.thresholdValue = thresholdValue;
		this.severity = severity;
		this.defaultAssignee = defaultAssignee.trim();
		this.status = "ACTIVE";
		this.creationRequestId = creationRequestId;
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

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getRuleCode() { return ruleCode; }
	String getName() { return name; }
	UUID getConnectionId() { return connectionId; }
	UUID getPointId() { return pointId; }
	String getRuleType() { return ruleType; }
	BigDecimal getThresholdValue() { return thresholdValue; }
	String getSeverity() { return severity; }
	String getDefaultAssignee() { return defaultAssignee; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "equipment", name = "alert_rule_events")
class EquipmentAlertRuleEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "rule_id") private UUID ruleId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected EquipmentAlertRuleEventEntity() { }

	EquipmentAlertRuleEventEntity(EquipmentAlertRuleEntity rule, UUID actorId, String action, String fromStatus,
			String toStatus, String reason, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = rule.getTenantOrganizationId();
		this.workspaceId = rule.getWorkspaceId();
		this.actorUserId = actorId;
		this.ruleId = rule.getId();
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
@Table(schema = "equipment", name = "alerts")
class EquipmentAlertEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "alert_number") private String alertNumber;
	@Column(name = "rule_id") private UUID ruleId;
	@Column(name = "rule_code_snapshot") private String ruleCodeSnapshot;
	@Column(name = "rule_name_snapshot") private String ruleNameSnapshot;
	@Column(name = "asset_id") private UUID assetId;
	@Column(name = "asset_code_snapshot") private String assetCodeSnapshot;
	@Column(name = "asset_name_snapshot") private String assetNameSnapshot;
	@Column(name = "connection_id") private UUID connectionId;
	@Column(name = "connection_code_snapshot") private String connectionCodeSnapshot;
	@Column(name = "point_id") private UUID pointId;
	@Column(name = "point_code_snapshot") private String pointCodeSnapshot;
	@Column(name = "point_name_snapshot") private String pointNameSnapshot;
	@Column(name = "rule_type") private String ruleType;
	private String severity;
	private String status;
	@Column(name = "condition_active") private boolean conditionActive;
	@Column(name = "observed_value") private BigDecimal observedValue;
	@Column(name = "observed_quality") private String observedQuality;
	@Column(name = "failure_code") private String failureCode;
	private String assignee;
	@Column(name = "resolution_notes") private String resolutionNotes;
	@Column(name = "linked_work_order_id") private UUID linkedWorkOrderId;
	@Version private long version;
	@Column(name = "first_occurred_at") private Instant firstOccurredAt;
	@Column(name = "last_occurred_at") private Instant lastOccurredAt;
	@Column(name = "recovered_at") private Instant recoveredAt;
	@Column(name = "acknowledged_at") private Instant acknowledgedAt;
	@Column(name = "processing_started_at") private Instant processingStartedAt;
	@Column(name = "resolved_at") private Instant resolvedAt;
	@Column(name = "closed_at") private Instant closedAt;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentAlertEntity() { }

	EquipmentAlertEntity(String number, EquipmentAlertRuleEntity rule, EquipmentTelemetryConnectionEntity connection,
			EquipmentAssetEntity asset, EquipmentTelemetryPointEntity point, BigDecimal value, String quality,
			String failureCode, Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = connection.getTenantOrganizationId();
		this.workspaceId = connection.getWorkspaceId();
		this.alertNumber = number;
		this.ruleId = rule.getId();
		this.ruleCodeSnapshot = rule.getRuleCode();
		this.ruleNameSnapshot = rule.getName();
		this.assetId = asset.getId();
		this.assetCodeSnapshot = asset.getAssetCode();
		this.assetNameSnapshot = asset.getAssetName();
		this.connectionId = connection.getId();
		this.connectionCodeSnapshot = connection.getConnectionCode();
		this.pointId = point == null ? null : point.getId();
		this.pointCodeSnapshot = point == null ? null : point.getPointCode();
		this.pointNameSnapshot = point == null ? null : point.getName();
		this.ruleType = rule.getRuleType();
		this.severity = rule.getSeverity();
		this.status = "OPEN";
		this.conditionActive = true;
		this.observedValue = value;
		this.observedQuality = quality;
		this.failureCode = failureCode;
		this.assignee = rule.getDefaultAssignee();
		this.firstOccurredAt = occurredAt;
		this.lastOccurredAt = occurredAt;
		this.createdAt = occurredAt;
		this.updatedAt = occurredAt;
	}

	void observe(BigDecimal value, String quality, String nextFailureCode, Instant occurredAt) {
		this.observedValue = value;
		this.observedQuality = quality;
		this.failureCode = nextFailureCode;
		this.lastOccurredAt = occurredAt;
		this.updatedAt = occurredAt;
	}

	void reopen(BigDecimal value, String quality, String nextFailureCode, Instant occurredAt) {
		this.status = "OPEN";
		this.conditionActive = true;
		this.observedValue = value;
		this.observedQuality = quality;
		this.failureCode = nextFailureCode;
		this.lastOccurredAt = occurredAt;
		this.recoveredAt = null;
		this.acknowledgedAt = null;
		this.processingStartedAt = null;
		this.resolvedAt = null;
		this.resolutionNotes = null;
		this.updatedBy = null;
		this.updatedAt = occurredAt;
	}

	void clearCondition(Instant occurredAt) {
		this.conditionActive = false;
		this.recoveredAt = occurredAt;
		this.updatedAt = occurredAt;
	}

	void acknowledge(String nextAssignee, UUID actorId) {
		this.status = "ACKNOWLEDGED";
		if (nextAssignee != null && !nextAssignee.isBlank()) this.assignee = nextAssignee.trim();
		this.acknowledgedAt = Instant.now();
		touch(actorId);
	}

	void startProcessing(UUID actorId) {
		this.status = "IN_PROGRESS";
		this.processingStartedAt = Instant.now();
		touch(actorId);
	}

	void resolve(String notes, UUID actorId) {
		this.status = "RESOLVED";
		this.resolutionNotes = notes.trim();
		this.resolvedAt = Instant.now();
		touch(actorId);
	}

	void close(UUID actorId) {
		this.status = "CLOSED";
		this.closedAt = Instant.now();
		touch(actorId);
	}

	void linkRepair(UUID workOrderId, UUID actorId) {
		this.linkedWorkOrderId = workOrderId;
		touch(actorId);
	}

	private void touch(UUID actorId) {
		this.updatedBy = actorId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getAlertNumber() { return alertNumber; }
	UUID getRuleId() { return ruleId; }
	String getRuleCodeSnapshot() { return ruleCodeSnapshot; }
	String getRuleNameSnapshot() { return ruleNameSnapshot; }
	UUID getAssetId() { return assetId; }
	String getAssetCodeSnapshot() { return assetCodeSnapshot; }
	String getAssetNameSnapshot() { return assetNameSnapshot; }
	UUID getConnectionId() { return connectionId; }
	String getConnectionCodeSnapshot() { return connectionCodeSnapshot; }
	UUID getPointId() { return pointId; }
	String getPointCodeSnapshot() { return pointCodeSnapshot; }
	String getPointNameSnapshot() { return pointNameSnapshot; }
	String getRuleType() { return ruleType; }
	String getSeverity() { return severity; }
	String getStatus() { return status; }
	boolean isConditionActive() { return conditionActive; }
	BigDecimal getObservedValue() { return observedValue; }
	String getObservedQuality() { return observedQuality; }
	String getFailureCode() { return failureCode; }
	String getAssignee() { return assignee; }
	String getResolutionNotes() { return resolutionNotes; }
	UUID getLinkedWorkOrderId() { return linkedWorkOrderId; }
	long getVersion() { return version; }
	Instant getFirstOccurredAt() { return firstOccurredAt; }
	Instant getLastOccurredAt() { return lastOccurredAt; }
	Instant getRecoveredAt() { return recoveredAt; }
	Instant getAcknowledgedAt() { return acknowledgedAt; }
	Instant getProcessingStartedAt() { return processingStartedAt; }
	Instant getResolvedAt() { return resolvedAt; }
	Instant getClosedAt() { return closedAt; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "equipment", name = "alert_events")
class EquipmentAlertEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "alert_id") private UUID alertId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected EquipmentAlertEventEntity() { }

	EquipmentAlertEventEntity(EquipmentAlertEntity alert, UUID actorId, String action, String fromStatus,
			String toStatus, String reason, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = alert.getTenantOrganizationId();
		this.workspaceId = alert.getWorkspaceId();
		this.actorUserId = actorId;
		this.alertId = alert.getId();
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
