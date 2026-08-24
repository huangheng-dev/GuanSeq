package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(schema = "finance", name = "advances")
class AdvanceEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "advance_number") private String advanceNumber;
	private String type;
	@Column(name = "party_type") private String partyType;
	@Column(name = "party_id") private UUID partyId;
	@Column(name = "party_code") private String partyCode;
	@Column(name = "party_name") private String partyName;
	private String currency;
	@Column(name = "advance_date") private LocalDate advanceDate;
	@Column(name = "total_amount") private BigDecimal totalAmount;
	@Column(name = "applied_amount") private BigDecimal appliedAmount;
	@Column(name = "refunded_amount") private BigDecimal refundedAmount;
	private String status;
	private String note;
	@Column(name = "request_id") private String requestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	@OneToMany(mappedBy = "advance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<AdvanceApplicationEntity> applications = new ArrayList<>();

	@OneToMany(mappedBy = "advance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<AdvanceRefundEntity> refunds = new ArrayList<>();

	protected AdvanceEntity() { }

	AdvanceEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String advanceNumber, String type, String partyType, UUID partyId,
			String partyCode, String partyName, LocalDate advanceDate,
			BigDecimal totalAmount, String note, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.advanceNumber = advanceNumber;
		this.type = type;
		this.partyType = partyType;
		this.partyId = partyId;
		this.partyCode = partyCode;
		this.partyName = partyName;
		this.currency = "CNY";
		this.advanceDate = advanceDate;
		this.totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
		this.appliedAmount = BigDecimal.ZERO.setScale(2);
		this.refundedAmount = BigDecimal.ZERO.setScale(2);
		this.status = "OPEN";
		this.note = note;
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	BigDecimal availableBalance() {
		return totalAmount.subtract(appliedAmount).subtract(refundedAmount).setScale(2, RoundingMode.HALF_UP);
	}

	AdvanceApplicationEntity apply(UUID invoiceId, String invoiceNumber, BigDecimal amount,
			LocalDate applicationDate, String requestId, UUID actorUserId) {
		BigDecimal newApplied = appliedAmount.add(amount).setScale(2, RoundingMode.HALF_UP);
		if (newApplied.add(refundedAmount).compareTo(totalAmount) > 0) {
			throw new IllegalStateException("抵扣金额超过可用余额");
		}
		this.appliedAmount = newApplied;
		recalculateStatus(actorUserId);
		AdvanceApplicationEntity application = new AdvanceApplicationEntity(this, invoiceId, invoiceNumber,
				amount, applicationDate, requestId, actorUserId);
		applications.add(application);
		return application;
	}

	AdvanceRefundEntity refund(BigDecimal amount, LocalDate refundDate, String reason,
			String requestId, UUID actorUserId) {
		BigDecimal newRefunded = refundedAmount.add(amount).setScale(2, RoundingMode.HALF_UP);
		if (newRefunded.add(appliedAmount).compareTo(totalAmount) > 0) {
			throw new IllegalStateException("退款金额超过可用余额");
		}
		this.refundedAmount = newRefunded;
		recalculateStatus(actorUserId);
		AdvanceRefundEntity refund = new AdvanceRefundEntity(this, amount, refundDate, reason,
				requestId, actorUserId);
		refunds.add(refund);
		return refund;
	}

	private void recalculateStatus(UUID actorUserId) {
		BigDecimal used = appliedAmount.add(refundedAmount);
		if (used.compareTo(totalAmount) >= 0) {
			this.status = "CLOSED";
		} else if (used.signum() > 0) {
			this.status = "PARTIALLY_USED";
		} else {
			this.status = "OPEN";
		}
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getAdvanceNumber() { return advanceNumber; }
	String getType() { return type; }
	String getPartyType() { return partyType; }
	UUID getPartyId() { return partyId; }
	String getPartyCode() { return partyCode; }
	String getPartyName() { return partyName; }
	String getCurrency() { return currency; }
	LocalDate getAdvanceDate() { return advanceDate; }
	BigDecimal getTotalAmount() { return totalAmount; }
	BigDecimal getAppliedAmount() { return appliedAmount; }
	BigDecimal getRefundedAmount() { return refundedAmount; }
	String getStatus() { return status; }
	String getNote() { return note; }
	String getRequestId() { return requestId; }
	long getVersion() { return version; }
	UUID getCreatedBy() { return createdBy; }
	Instant getCreatedAt() { return createdAt; }
	UUID getUpdatedBy() { return updatedBy; }
	Instant getUpdatedAt() { return updatedAt; }
	List<AdvanceApplicationEntity> getApplications() { return applications; }
	List<AdvanceRefundEntity> getRefunds() { return refunds; }
}

@Entity
@Table(schema = "finance", name = "advance_applications")
class AdvanceApplicationEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "advance_id", nullable = false) private UUID advanceId;
	@Column(name = "invoice_id") private UUID invoiceId;
	@Column(name = "invoice_number") private String invoiceNumber;
	@Column(name = "applied_amount") private BigDecimal appliedAmount;
	@Column(name = "application_date") private LocalDate applicationDate;
	@Column(name = "request_id") private String requestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;

	@jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "advance_id", insertable = false, updatable = false)
	private AdvanceEntity advance;

	protected AdvanceApplicationEntity() { }

	AdvanceApplicationEntity(AdvanceEntity parent, UUID invoiceId, String invoiceNumber,
			BigDecimal appliedAmount, LocalDate applicationDate, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = parent.getTenantOrganizationId();
		this.workspaceId = parent.getWorkspaceId();
		this.advanceId = parent.getId();
		this.invoiceId = invoiceId;
		this.invoiceNumber = invoiceNumber;
		this.appliedAmount = appliedAmount.setScale(2, RoundingMode.HALF_UP);
		this.applicationDate = applicationDate;
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getAdvanceId() { return advanceId; }
	UUID getInvoiceId() { return invoiceId; }
	String getInvoiceNumber() { return invoiceNumber; }
	BigDecimal getAppliedAmount() { return appliedAmount; }
	LocalDate getApplicationDate() { return applicationDate; }
	String getRequestId() { return requestId; }
	UUID getCreatedBy() { return createdBy; }
	Instant getCreatedAt() { return createdAt; }
}

@Entity
@Table(schema = "finance", name = "advance_refunds")
class AdvanceRefundEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "advance_id", nullable = false) private UUID advanceId;
	@Column(name = "refund_amount") private BigDecimal refundAmount;
	@Column(name = "refund_date") private LocalDate refundDate;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;

	@jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "advance_id", insertable = false, updatable = false)
	private AdvanceEntity advance;

	protected AdvanceRefundEntity() { }

	AdvanceRefundEntity(AdvanceEntity parent, BigDecimal refundAmount, LocalDate refundDate,
			String reason, String requestId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = parent.getTenantOrganizationId();
		this.workspaceId = parent.getWorkspaceId();
		this.advanceId = parent.getId();
		this.refundAmount = refundAmount.setScale(2, RoundingMode.HALF_UP);
		this.refundDate = refundDate;
		this.reason = reason;
		this.requestId = requestId;
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getAdvanceId() { return advanceId; }
	BigDecimal getRefundAmount() { return refundAmount; }
	LocalDate getRefundDate() { return refundDate; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	UUID getCreatedBy() { return createdBy; }
	Instant getCreatedAt() { return createdAt; }
}

@Entity
@Table(schema = "finance", name = "advance_events")
class AdvanceEventEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "advance_id") private UUID advanceId;
	@Column(name = "application_id") private UUID applicationId;
	@Column(name = "refund_id") private UUID refundId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
	private java.util.Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected AdvanceEventEntity() { }

	AdvanceEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID advanceId, UUID applicationId, UUID refundId,
			String action, String fromStatus, String toStatus,
			String requestId, java.util.Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.advanceId = advanceId;
		this.applicationId = applicationId;
		this.refundId = refundId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.requestId = requestId;
		this.details = details == null ? java.util.Map.of() : details;
		this.occurredAt = Instant.now();
	}
}
