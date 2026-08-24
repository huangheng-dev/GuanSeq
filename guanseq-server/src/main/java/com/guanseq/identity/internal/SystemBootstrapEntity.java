package com.guanseq.identity.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "identity", name = "system_bootstrap")
class SystemBootstrapEntity {

	@Id
	@Column(name = "singleton_key")
	private Boolean singletonKey;

	private String status;

	@Column(name = "initial_user_id")
	private UUID initialUserId;

	@Column(name = "initial_workspace_id")
	private UUID initialWorkspaceId;

	@Column(name = "request_id")
	private String requestId;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected SystemBootstrapEntity() {
	}

	boolean isPending() {
		return "PENDING".equals(status);
	}

	void complete(UUID userId, UUID workspaceId, String completedRequestId) {
		this.status = "COMPLETED";
		this.initialUserId = userId;
		this.initialWorkspaceId = workspaceId;
		this.requestId = completedRequestId;
		this.completedAt = Instant.now();
	}
}
