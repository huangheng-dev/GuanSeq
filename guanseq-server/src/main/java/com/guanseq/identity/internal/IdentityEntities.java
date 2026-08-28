package com.guanseq.identity.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "identity", name = "organization_units")
class OrganizationUnitEntity {

	@Id
	private UUID id;

	private String code;

	private String name;

	@Column(name = "unit_type")
	private String unitType;

	@Column(name = "parent_id")
	private UUID parentId;

	private String status;

	@Column(name = "responsible_user_id")
	private UUID responsibleUserId;

	@Version
	private long version;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	protected OrganizationUnitEntity() {
	}

	OrganizationUnitEntity(UUID id, String code, String name, String unitType, UUID parentId) {
		this.id = id;
		this.code = code;
		this.name = name;
		this.unitType = unitType;
		this.parentId = parentId;
		this.status = "ACTIVE";
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	UUID getId() {
		return id;
	}

	String getName() {
		return name;
	}

	String getCode() { return code; }
	String getUnitType() { return unitType; }
	UUID getParentId() { return parentId; }
	String getStatus() { return status; }
	UUID getResponsibleUserId() { return responsibleUserId; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }

	void update(String nextName, UUID nextResponsibleUserId) {
		this.name = nextName;
		this.responsibleUserId = nextResponsibleUserId;
		this.updatedAt = Instant.now();
	}

	void changeStatus(String nextStatus) {
		this.status = nextStatus;
		this.updatedAt = Instant.now();
	}
}

@Entity
@Table(schema = "identity", name = "workspaces")
class WorkspaceEntity {

	@Id
	private UUID id;

	private String code;

	private String name;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_organization_id")
	private OrganizationUnitEntity tenantOrganization;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "operating_organization_id")
	private OrganizationUnitEntity operatingOrganization;

	private String status;

	@Column(name = "responsible_user_id")
	private UUID responsibleUserId;

	@Version
	private long version;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	protected WorkspaceEntity() {
	}

	WorkspaceEntity(
			UUID id,
			String code,
			String name,
			OrganizationUnitEntity tenantOrganization,
			OrganizationUnitEntity operatingOrganization) {
		this.id = id;
		this.code = code;
		this.name = name;
		this.tenantOrganization = tenantOrganization;
		this.operatingOrganization = operatingOrganization;
		this.status = "ACTIVE";
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	UUID getId() {
		return id;
	}

	String getCode() {
		return code;
	}

	String getName() {
		return name;
	}

	OrganizationUnitEntity getTenantOrganization() {
		return tenantOrganization;
	}

	OrganizationUnitEntity getOperatingOrganization() {
		return operatingOrganization;
	}

	String getStatus() {
		return status;
	}

	UUID getResponsibleUserId() { return responsibleUserId; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }

	void update(String nextName, UUID nextResponsibleUserId) {
		this.name = nextName;
		this.responsibleUserId = nextResponsibleUserId;
		this.updatedAt = Instant.now();
	}
}

@Entity
@Table(schema = "identity", name = "user_accounts")
class IdentityUserEntity {

	@Id
	private UUID id;

	@Column(name = "tenant_organization_id")
	private UUID tenantOrganizationId;

	private String username;

	@Column(name = "display_name")
	private String displayName;

	private String status;

	@Version
	private long version;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	protected IdentityUserEntity() {
	}

	IdentityUserEntity(UUID id, UUID tenantOrganizationId, String username, String displayName) {
		this.id = id;
		this.tenantOrganizationId = tenantOrganizationId;
		this.username = username;
		this.displayName = displayName;
		this.status = "ACTIVE";
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	UUID getId() {
		return id;
	}

	String getUsername() {
		return username;
	}

	String getDisplayName() {
		return displayName;
	}

	UUID getTenantOrganizationId() {
		return tenantOrganizationId;
	}

	String getStatus() {
		return status;
	}

	long getVersion() {
		return version;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

	void updateDisplayName(String nextDisplayName) {
		this.displayName = nextDisplayName;
		this.updatedAt = Instant.now();
	}
}

@Entity
@Table(schema = "identity", name = "workspace_memberships")
class WorkspaceMembershipEntity {

	@Id
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "workspace_id")
	private UUID workspaceId;

	@Column(name = "role_code")
	private String roleCode;

	private String status;

	@Column(name = "organization_unit_id")
	private UUID organizationUnitId;

	@Version
	private long version;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	protected WorkspaceMembershipEntity() {
	}

	WorkspaceMembershipEntity(UUID id, UUID userId, UUID workspaceId, UUID organizationUnitId, String roleCode) {
		this.id = id;
		this.userId = userId;
		this.workspaceId = workspaceId;
		this.organizationUnitId = organizationUnitId;
		this.roleCode = roleCode;
		this.status = "ACTIVE";
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	UUID getId() {
		return id;
	}

	UUID getUserId() {
		return userId;
	}

	UUID getWorkspaceId() {
		return workspaceId;
	}

	String getRoleCode() {
		return roleCode;
	}

	String getStatus() {
		return status;
	}

	UUID getOrganizationUnitId() { return organizationUnitId; }

	long getVersion() {
		return version;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

	void updateRole(String nextRoleCode) {
		this.roleCode = nextRoleCode;
		this.updatedAt = Instant.now();
	}

	void changeStatus(String nextStatus) {
		this.status = nextStatus;
		this.updatedAt = Instant.now();
	}

	void assignOrganization(UUID nextOrganizationUnitId) {
		this.organizationUnitId = nextOrganizationUnitId;
		this.updatedAt = Instant.now();
	}
}

@Entity
@Table(schema = "identity", name = "user_workspace_preferences")
class UserWorkspacePreferenceEntity {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "current_workspace_id")
	private UUID currentWorkspaceId;

	@Version
	private long version;

	@Column(name = "updated_at")
	private Instant updatedAt;

	protected UserWorkspacePreferenceEntity() {
	}

	UserWorkspacePreferenceEntity(UUID userId, UUID currentWorkspaceId) {
		this.userId = userId;
		this.currentWorkspaceId = currentWorkspaceId;
		this.updatedAt = Instant.now();
	}

	UUID getCurrentWorkspaceId() {
		return currentWorkspaceId;
	}

	long getVersion() {
		return version;
	}

	void select(UUID workspaceId) {
		this.currentWorkspaceId = workspaceId;
		this.updatedAt = Instant.now();
	}
}

@Entity
@Table(schema = "identity", name = "audit_events")
class AuditEventEntity {

	@Id
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "workspace_id")
	private UUID workspaceId;

	@Column(name = "event_type")
	private String eventType;

	@Column(name = "object_type")
	private String objectType;

	@Column(name = "object_id")
	private String objectId;

	@Column(name = "request_id")
	private String requestId;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> details;

	@Column(name = "occurred_at")
	private Instant occurredAt;

	protected AuditEventEntity() {
	}

	AuditEventEntity(
			UUID userId,
			UUID workspaceId,
			String eventType,
			String objectType,
			String objectId,
			String requestId,
			Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.workspaceId = workspaceId;
		this.eventType = eventType;
		this.objectType = objectType;
		this.objectId = objectId;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getUserId() { return userId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getEventType() { return eventType; }
	String getObjectType() { return objectType; }
	String getObjectId() { return objectId; }
	String getRequestId() { return requestId; }
	Map<String, Object> getDetails() { return details; }
	Instant getOccurredAt() { return occurredAt; }
}
