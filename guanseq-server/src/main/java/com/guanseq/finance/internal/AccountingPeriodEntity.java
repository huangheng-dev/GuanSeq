package com.guanseq.finance.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "finance", name = "accounting_periods")
class AccountingPeriodEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "fiscal_year") private int fiscalYear;
	@Column(name = "fiscal_period") private int fiscalPeriod;
	private String status;
	@Column(name = "closed_at") private Instant closedAt;
	@Column(name = "closed_by") private UUID closedBy;
	@Column(name = "reopened_at") private Instant reopenedAt;
	@Column(name = "reopened_by") private UUID reopenedBy;
	@Column(name = "reopen_reason") private String reopenReason;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected AccountingPeriodEntity() { }

	AccountingPeriodEntity(UUID id, UUID tenantOrganizationId, UUID workspaceId, int fiscalYear, int fiscalPeriod,
			UUID actorUserId, String requestId) {
		Instant now = Instant.now();
		this.id = id;
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.fiscalYear = fiscalYear;
		this.fiscalPeriod = fiscalPeriod;
		this.status = "OPEN";
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = now;
		this.updatedBy = actorUserId;
		this.updatedAt = now;
	}

	void close(UUID actorUserId) {
		this.status = "CLOSED";
		this.closedAt = Instant.now();
		this.closedBy = actorUserId;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void reopen(UUID actorUserId, String reason) {
		this.status = "OPEN";
		this.closedAt = null;
		this.closedBy = null;
		this.reopenedAt = Instant.now();
		this.reopenedBy = actorUserId;
		this.reopenReason = reason;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getTenantOrganizationId() { return tenantOrganizationId; }
	public void setTenantOrganizationId(UUID tenantOrganizationId) { this.tenantOrganizationId = tenantOrganizationId; }
	public UUID getWorkspaceId() { return workspaceId; }
	public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
	public int getFiscalYear() { return fiscalYear; }
	public void setFiscalYear(int fiscalYear) { this.fiscalYear = fiscalYear; }
	public int getFiscalPeriod() { return fiscalPeriod; }
	public void setFiscalPeriod(int fiscalPeriod) { this.fiscalPeriod = fiscalPeriod; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public Instant getClosedAt() { return closedAt; }
	public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
	public UUID getClosedBy() { return closedBy; }
	public void setClosedBy(UUID closedBy) { this.closedBy = closedBy; }
	public Instant getReopenedAt() { return reopenedAt; }
	public void setReopenedAt(Instant reopenedAt) { this.reopenedAt = reopenedAt; }
	public UUID getReopenedBy() { return reopenedBy; }
	public void setReopenedBy(UUID reopenedBy) { this.reopenedBy = reopenedBy; }
	public String getReopenReason() { return reopenReason; }
	public void setReopenReason(String reopenReason) { this.reopenReason = reopenReason; }
	public String getRequestId() { return requestId; }
	public void setRequestId(String requestId) { this.requestId = requestId; }
	public long getVersion() { return version; }
	public void setVersion(long version) { this.version = version; }
	public UUID getCreatedBy() { return createdBy; }
	public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
	public UUID getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
