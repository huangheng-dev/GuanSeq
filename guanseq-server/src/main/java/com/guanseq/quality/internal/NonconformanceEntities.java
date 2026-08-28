package com.guanseq.quality.internal;

import java.math.BigDecimal;
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
@Table(schema = "quality", name = "nonconformances")
class NonconformanceEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "case_number") private String caseNumber;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "inspection_id") private UUID inspectionId;
	@Column(name = "inspection_number") private String inspectionNumber;
	@Column(name = "source_document_id") private UUID sourceDocumentId;
	@Column(name = "source_document_number") private String sourceDocumentNumber;
	@Column(name = "order_id") private UUID orderId;
	@Column(name = "order_number") private String orderNumber;
	@Column(name = "supplier_id") private UUID supplierId;
	@Column(name = "supplier_code") private String supplierCode;
	@Column(name = "supplier_name") private String supplierName;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	@Column(name = "nonconforming_quantity") private BigDecimal nonconformingQuantity;
	@Column(name = "defect_description") private String defectDescription;
	private String status;
	private String severity;
	@Column(name = "immediate_containment") private String immediateContainment;
	@Column(name = "review_conclusion") private String reviewConclusion;
	@Column(name = "capa_required") private Boolean capaRequired;
	@Column(name = "reviewed_by") private UUID reviewedBy;
	@Column(name = "reviewed_at") private Instant reviewedAt;
	@Column(name = "disposition_type") private String dispositionType;
	@Column(name = "disposition_decision") private String dispositionDecision;
	@Column(name = "disposition_evidence") private String dispositionEvidence;
	@Column(name = "disposition_owner") private String dispositionOwner;
	@Column(name = "disposed_by") private UUID disposedBy;
	@Column(name = "disposed_at") private Instant disposedAt;
	@Column(name = "root_cause") private String rootCause;
	@Column(name = "corrective_action") private String correctiveAction;
	@Column(name = "action_owner") private String actionOwner;
	@Column(name = "action_due_date") private LocalDate actionDueDate;
	@Column(name = "action_completion_evidence") private String actionCompletionEvidence;
	@Column(name = "action_completed_by") private UUID actionCompletedBy;
	@Column(name = "action_completed_at") private Instant actionCompletedAt;
	@Column(name = "verification_effective") private Boolean verificationEffective;
	@Column(name = "verification_conclusion") private String verificationConclusion;
	@Column(name = "verified_by") private UUID verifiedBy;
	@Column(name = "verified_at") private Instant verifiedAt;
	@Column(name = "closed_at") private Instant closedAt;
	@Column(name = "create_request_id") private String createRequestId;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected NonconformanceEntity() { }

	NonconformanceEntity(FinalInspectionEntity inspection, String caseNumber, String requestId, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = inspection.getTenantOrganizationId();
		this.owningOrganizationId = inspection.getOwningOrganizationId();
		this.workspaceId = inspection.getWorkspaceId();
		this.caseNumber = caseNumber;
		this.sourceType = "INCOMING".equals(inspection.getInspectionType()) ? "INCOMING_INSPECTION" : "FINAL_INSPECTION";
		this.inspectionId = inspection.getId();
		this.inspectionNumber = inspection.getInspectionNumber();
		this.sourceDocumentId = inspection.getSourceId();
		this.sourceDocumentNumber = inspection.getSourceNumber();
		this.orderId = inspection.getOrderId();
		this.orderNumber = inspection.getOrderNumber();
		this.supplierId = inspection.getSupplierId();
		this.supplierCode = inspection.getSupplierCode();
		this.supplierName = inspection.getSupplierName();
		this.materialId = inspection.getMaterialId();
		this.materialCode = inspection.getMaterialCode();
		this.materialName = inspection.getMaterialName();
		this.materialSpecification = inspection.getMaterialSpecification();
		this.unit = inspection.getUnit();
		this.nonconformingQuantity = inspection.getRejectedQuantity();
		this.defectDescription = inspection.getDefectDescription();
		this.status = "OPEN";
		this.createRequestId = requestId;
		this.createdBy = actorId;
		this.createdAt = Instant.now();
		this.updatedBy = actorId;
		this.updatedAt = this.createdAt;
	}

	void review(String severity, String containment, String conclusion, boolean capaRequired, UUID actorId) {
		this.severity = severity;
		this.immediateContainment = containment;
		this.reviewConclusion = conclusion;
		this.capaRequired = capaRequired;
		this.reviewedBy = actorId;
		this.reviewedAt = Instant.now();
		this.status = "REVIEWED";
		touch(actorId);
	}

	void dispose(String type, String decision, String evidence, String owner, UUID actorId) {
		this.dispositionType = type;
		this.dispositionDecision = decision;
		this.dispositionEvidence = evidence;
		this.dispositionOwner = owner;
		this.disposedBy = actorId;
		this.disposedAt = Instant.now();
		this.verificationEffective = null;
		this.verificationConclusion = null;
		this.verifiedBy = null;
		this.verifiedAt = null;
		this.status = Boolean.TRUE.equals(capaRequired) ? "ACTION_REQUIRED" : "VERIFICATION_PENDING";
		touch(actorId);
	}

	void planAction(String rootCause, String action, String owner, LocalDate dueDate, UUID actorId) {
		this.rootCause = rootCause;
		this.correctiveAction = action;
		this.actionOwner = owner;
		this.actionDueDate = dueDate;
		this.actionCompletionEvidence = null;
		this.actionCompletedBy = null;
		this.actionCompletedAt = null;
		this.verificationEffective = null;
		this.verificationConclusion = null;
		this.verifiedBy = null;
		this.verifiedAt = null;
		this.status = "ACTION_IN_PROGRESS";
		touch(actorId);
	}

	void completeAction(String evidence, UUID actorId) {
		this.actionCompletionEvidence = evidence;
		this.actionCompletedBy = actorId;
		this.actionCompletedAt = Instant.now();
		this.status = "VERIFICATION_PENDING";
		touch(actorId);
	}

	void verify(boolean effective, String conclusion, UUID actorId) {
		this.verificationEffective = effective;
		this.verificationConclusion = conclusion;
		this.verifiedBy = actorId;
		this.verifiedAt = Instant.now();
		if (effective) {
			this.status = "CLOSED";
			this.closedAt = this.verifiedAt;
		} else {
			this.status = Boolean.TRUE.equals(capaRequired) ? "ACTION_REQUIRED" : "REVIEWED";
			this.closedAt = null;
		}
		touch(actorId);
	}

	void reopen(UUID actorId) {
		this.verificationEffective = null;
		this.verificationConclusion = null;
		this.verifiedBy = null;
		this.verifiedAt = null;
		this.closedAt = null;
		this.status = Boolean.TRUE.equals(capaRequired) ? "ACTION_REQUIRED" : "REVIEWED";
		touch(actorId);
	}

	private void touch(UUID actorId) { this.updatedBy = actorId; this.updatedAt = Instant.now(); }

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	String getCaseNumber() { return caseNumber; }
	String getSourceType() { return sourceType; }
	UUID getInspectionId() { return inspectionId; }
	String getInspectionNumber() { return inspectionNumber; }
	UUID getSourceDocumentId() { return sourceDocumentId; }
	String getSourceDocumentNumber() { return sourceDocumentNumber; }
	UUID getOrderId() { return orderId; }
	String getOrderNumber() { return orderNumber; }
	UUID getSupplierId() { return supplierId; }
	String getSupplierCode() { return supplierCode; }
	String getSupplierName() { return supplierName; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getNonconformingQuantity() { return nonconformingQuantity; }
	String getDefectDescription() { return defectDescription; }
	String getStatus() { return status; }
	String getSeverity() { return severity; }
	String getImmediateContainment() { return immediateContainment; }
	String getReviewConclusion() { return reviewConclusion; }
	Boolean getCapaRequired() { return capaRequired; }
	String getDispositionType() { return dispositionType; }
	String getDispositionDecision() { return dispositionDecision; }
	String getDispositionEvidence() { return dispositionEvidence; }
	String getDispositionOwner() { return dispositionOwner; }
	String getRootCause() { return rootCause; }
	String getCorrectiveAction() { return correctiveAction; }
	String getActionOwner() { return actionOwner; }
	LocalDate getActionDueDate() { return actionDueDate; }
	String getActionCompletionEvidence() { return actionCompletionEvidence; }
	Boolean getVerificationEffective() { return verificationEffective; }
	String getVerificationConclusion() { return verificationConclusion; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
	Instant getClosedAt() { return closedAt; }
}

@Entity
@Table(schema = "quality", name = "nonconformance_events")
class NonconformanceEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "nonconformance_id") private UUID nonconformanceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "actor_username") private String actorUsername;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected NonconformanceEventEntity() { }
	NonconformanceEventEntity(UUID tenantId, UUID workspaceId, UUID caseId, UUID actorId, String actorUsername,
			String action, String from, String to, String reason, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID(); this.tenantOrganizationId = tenantId; this.workspaceId = workspaceId;
		this.nonconformanceId = caseId; this.actorUserId = actorId; this.actorUsername = actorUsername;
		this.action = action; this.fromStatus = from; this.toStatus = to; this.reason = reason;
		this.requestId = requestId; this.details = details; this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getNonconformanceId() { return nonconformanceId; }
	UUID getActorUserId() { return actorUserId; }
	String getActorUsername() { return actorUsername; }
	String getAction() { return action; }
	String getFromStatus() { return fromStatus; }
	String getToStatus() { return toStatus; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	Map<String, Object> getDetails() { return details; }
	Instant getOccurredAt() { return occurredAt; }
}
