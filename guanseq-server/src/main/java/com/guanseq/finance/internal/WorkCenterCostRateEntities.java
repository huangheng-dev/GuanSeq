package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Table(schema = "finance", name = "work_center_cost_rates")
class WorkCenterCostRateEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "work_center_code") private String workCenterCode;
	@Column(name = "work_center_name") private String workCenterName;
	private String currency;
	@Column(name = "labor_rate_per_hour") private BigDecimal laborRatePerHour;
	@Column(name = "overhead_rate_per_hour") private BigDecimal overheadRatePerHour;
	@Column(name = "effective_date") private LocalDate effectiveDate;
	private String status;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected WorkCenterCostRateEntity() { }

	WorkCenterCostRateEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String workCenterCode, String workCenterName, String currency, BigDecimal laborRatePerHour,
			BigDecimal overheadRatePerHour, LocalDate effectiveDate, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.workCenterCode = workCenterCode.trim().toUpperCase();
		this.workCenterName = workCenterName.trim();
		this.currency = currency;
		this.laborRatePerHour = rate(laborRatePerHour);
		this.overheadRatePerHour = rate(overheadRatePerHour);
		this.effectiveDate = effectiveDate;
		this.status = "ACTIVE";
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = createdAt;
	}

	void changeStatus(String target, UUID actorUserId) {
		this.status = target;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	private static BigDecimal rate(BigDecimal value) { return value.setScale(6, RoundingMode.HALF_UP); }
	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getWorkCenterCode() { return workCenterCode; }
	String getWorkCenterName() { return workCenterName; }
	String getCurrency() { return currency; }
	BigDecimal getLaborRatePerHour() { return laborRatePerHour; }
	BigDecimal getOverheadRatePerHour() { return overheadRatePerHour; }
	LocalDate getEffectiveDate() { return effectiveDate; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "finance", name = "work_center_cost_rate_events")
class WorkCenterCostRateEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "rate_id") private UUID rateId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected WorkCenterCostRateEventEntity() { }

	WorkCenterCostRateEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID rateId,
			String action, String fromStatus, String toStatus, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.rateId = rateId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}

	UUID getRateId() { return rateId; }
}
