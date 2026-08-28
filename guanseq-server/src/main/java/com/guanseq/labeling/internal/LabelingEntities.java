package com.guanseq.labeling.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "labeling", name = "print_requests")
class LabelPrintRequestEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "request_number") private String requestNumber;
	@Column(name = "object_type") private String objectType;
	@Column(name = "object_id") private UUID objectId;
	@Column(name = "object_version") private long objectVersion;
	@Column(name = "object_code") private String objectCode;
	@Column(name = "object_name") private String objectName;
	@Column(name = "object_detail") private String objectDetail;
	private String payload;
	@Column(name = "template_code") private String templateCode;
	@Column(name = "template_version") private String templateVersion;
	private String mode;
	private int copies;
	private String reason;
	private String status;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "actor_username") private String actorUsername;
	@Column(name = "request_id") private String requestId;
	@Column(name = "prepared_at") private Instant preparedAt;

	protected LabelPrintRequestEntity() { }

	LabelPrintRequestEntity(UUID tenantOrganizationId, UUID workspaceId, String requestNumber, ResolvedLabel label,
			String mode, int copies, String reason, UUID actorUserId, String actorUsername, String requestId) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantOrganizationId; this.workspaceId = workspaceId;
		this.requestNumber = requestNumber; this.objectType = label.objectType(); this.objectId = label.objectId();
		this.objectVersion = label.objectVersion(); this.objectCode = label.objectCode(); this.objectName = label.objectName();
		this.objectDetail = label.objectDetail(); this.payload = label.payload(); this.templateCode = label.templateCode();
		this.templateVersion = label.templateVersion(); this.mode = mode; this.copies = copies; this.reason = reason;
		this.status = "PREPARED"; this.actorUserId = actorUserId; this.actorUsername = actorUsername;
		this.requestId = requestId; this.preparedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getRequestNumber() { return requestNumber; }
	String getObjectType() { return objectType; }
	UUID getObjectId() { return objectId; }
	long getObjectVersion() { return objectVersion; }
	String getObjectCode() { return objectCode; }
	String getObjectName() { return objectName; }
	String getObjectDetail() { return objectDetail; }
	String getPayload() { return payload; }
	String getTemplateCode() { return templateCode; }
	String getTemplateVersion() { return templateVersion; }
	String getMode() { return mode; }
	int getCopies() { return copies; }
	String getReason() { return reason; }
	String getStatus() { return status; }
	String getActorUsername() { return actorUsername; }
	String getRequestId() { return requestId; }
	Instant getPreparedAt() { return preparedAt; }
}

record ResolvedLabel(String objectType, UUID objectId, long objectVersion, String objectCode, String objectName,
		String objectDetail, String payload, String templateCode, String templateVersion) { }

