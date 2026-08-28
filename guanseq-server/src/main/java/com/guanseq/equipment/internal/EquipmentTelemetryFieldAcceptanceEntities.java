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
@Table(schema = "equipment", name = "telemetry_field_acceptances")
class EquipmentTelemetryFieldAcceptanceEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "connection_id") private UUID connectionId;
	@Column(name = "acceptance_number") private String acceptanceNumber;
	private String status;
	@Column(name = "network_approved") private boolean networkApproved;
	@Column(name = "security_validated") private boolean securityValidated;
	@Column(name = "read_only_confirmed") private boolean readOnlyConfirmed;
	@Column(name = "disconnect_recovery_verified") private boolean disconnectRecoveryVerified;
	@Column(name = "capacity_verified") private boolean capacityVerified;
	@Column(name = "point_mapping_approved") private boolean pointMappingApproved;
	@Column(name = "responsible_owner") private String responsibleOwner;
	@Column(name = "test_window_start") private Instant testWindowStart;
	@Column(name = "test_window_end") private Instant testWindowEnd;
	@Column(name = "evidence_reference") private String evidenceReference;
	private String notes;
	@Column(name = "rejection_reason") private String rejectionReason;
	@Version private long version;
	@Column(name = "creation_request_id") private String creationRequestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "submitted_by") private UUID submittedBy;
	@Column(name = "submitted_at") private Instant submittedAt;
	@Column(name = "approved_by") private UUID approvedBy;
	@Column(name = "approved_at") private Instant approvedAt;
	@Column(name = "rejected_by") private UUID rejectedBy;
	@Column(name = "rejected_at") private Instant rejectedAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentTelemetryFieldAcceptanceEntity() { }

	EquipmentTelemetryFieldAcceptanceEntity(EquipmentTelemetryConnectionEntity connection, String number,
			String requestId, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = connection.getTenantOrganizationId();
		this.owningOrganizationId = connection.getOwningOrganizationId();
		this.workspaceId = connection.getWorkspaceId();
		this.connectionId = connection.getId();
		this.acceptanceNumber = number;
		this.status = "DRAFT";
		this.creationRequestId = requestId;
		this.createdBy = actorId;
		this.createdAt = Instant.now();
		this.updatedBy = actorId;
		this.updatedAt = this.createdAt;
	}

	void update(EquipmentTelemetryFieldAcceptanceRecordValues values, UUID actorId) {
		this.networkApproved = values.networkApproved();
		this.securityValidated = values.securityValidated();
		this.readOnlyConfirmed = values.readOnlyConfirmed();
		this.disconnectRecoveryVerified = values.disconnectRecoveryVerified();
		this.capacityVerified = values.capacityVerified();
		this.pointMappingApproved = values.pointMappingApproved();
		this.responsibleOwner = nullable(values.responsibleOwner());
		this.testWindowStart = values.testWindowStart();
		this.testWindowEnd = values.testWindowEnd();
		this.evidenceReference = nullable(values.evidenceReference());
		this.notes = nullable(values.notes());
		this.status = "DRAFT";
		this.rejectionReason = null;
		touch(actorId);
	}

	void submit(UUID actorId) {
		this.status = "SUBMITTED";
		this.rejectionReason = null;
		this.submittedBy = actorId;
		this.submittedAt = Instant.now();
		touch(actorId);
	}

	void approve(UUID actorId) {
		this.status = "APPROVED";
		this.approvedBy = actorId;
		this.approvedAt = Instant.now();
		touch(actorId);
	}

	void reject(String reason, UUID actorId) {
		this.status = "REJECTED";
		this.rejectionReason = reason.trim();
		this.rejectedBy = actorId;
		this.rejectedAt = Instant.now();
		touch(actorId);
	}

	private void touch(UUID actorId) { this.updatedBy = actorId; this.updatedAt = Instant.now(); }
	private static String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	UUID getConnectionId() { return connectionId; }
	String getAcceptanceNumber() { return acceptanceNumber; }
	String getStatus() { return status; }
	boolean isNetworkApproved() { return networkApproved; }
	boolean isSecurityValidated() { return securityValidated; }
	boolean isReadOnlyConfirmed() { return readOnlyConfirmed; }
	boolean isDisconnectRecoveryVerified() { return disconnectRecoveryVerified; }
	boolean isCapacityVerified() { return capacityVerified; }
	boolean isPointMappingApproved() { return pointMappingApproved; }
	String getResponsibleOwner() { return responsibleOwner; }
	Instant getTestWindowStart() { return testWindowStart; }
	Instant getTestWindowEnd() { return testWindowEnd; }
	String getEvidenceReference() { return evidenceReference; }
	String getNotes() { return notes; }
	String getRejectionReason() { return rejectionReason; }
	long getVersion() { return version; }
	UUID getCreatedBy() { return createdBy; }
	Instant getCreatedAt() { return createdAt; }
	UUID getSubmittedBy() { return submittedBy; }
	Instant getSubmittedAt() { return submittedAt; }
	UUID getApprovedBy() { return approvedBy; }
	Instant getApprovedAt() { return approvedAt; }
	UUID getRejectedBy() { return rejectedBy; }
	Instant getRejectedAt() { return rejectedAt; }
	Instant getUpdatedAt() { return updatedAt; }
}

record EquipmentTelemetryFieldAcceptanceRecordValues(
		boolean networkApproved, boolean securityValidated, boolean readOnlyConfirmed,
		boolean disconnectRecoveryVerified, boolean capacityVerified, boolean pointMappingApproved,
		String responsibleOwner, Instant testWindowStart, Instant testWindowEnd,
		String evidenceReference, String notes) { }

@Entity
@Table(schema = "equipment", name = "telemetry_field_acceptance_events")
class EquipmentTelemetryFieldAcceptanceEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "acceptance_id") private UUID acceptanceId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected EquipmentTelemetryFieldAcceptanceEventEntity() { }

	EquipmentTelemetryFieldAcceptanceEventEntity(EquipmentTelemetryFieldAcceptanceEntity acceptance, UUID actorId,
			String action, String fromStatus, String toStatus, String reason, String requestId,
			Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = acceptance.getTenantOrganizationId();
		this.workspaceId = acceptance.getWorkspaceId();
		this.actorUserId = actorId;
		this.acceptanceId = acceptance.getId();
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
