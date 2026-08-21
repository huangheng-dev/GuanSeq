package com.guanseq.product.internal;

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
@Table(schema = "product", name = "routings")
class RoutingEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "routing_number") private String routingNumber;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	@Column(name = "material_unit") private String materialUnit;
	@Column(name = "usage_type") private String usageType;
	@Column(name = "version_code") private String versionCode;
	@Column(name = "base_quantity") private BigDecimal baseQuantity;
	@Column(name = "effective_from") private LocalDate effectiveFrom;
	@Column(name = "effective_to") private LocalDate effectiveTo;
	private String owner;
	@Column(name = "change_reason") private String changeReason;
	private String status;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;
	@Column(name = "published_by") private UUID publishedBy;
	@Column(name = "published_at") private Instant publishedAt;

	protected RoutingEntity() { }

	RoutingEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String routingNumber, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId;
		this.routingNumber = routingNumber;
		this.status = "DRAFT";
		this.createdBy = actorId;
		this.createdAt = Instant.now();
		this.updatedBy = actorId;
		this.updatedAt = this.createdAt;
	}

	void updateDraft(UUID materialId, String code, String name, String specification, String unit,
			String usageType, String versionCode, BigDecimal baseQuantity, LocalDate effectiveFrom,
			String owner, String changeReason, UUID actorId) {
		this.materialId = materialId;
		this.materialCode = code;
		this.materialName = name;
		this.materialSpecification = specification;
		this.materialUnit = unit;
		this.usageType = usageType;
		this.versionCode = versionCode.trim();
		this.baseQuantity = baseQuantity;
		this.effectiveFrom = effectiveFrom;
		this.effectiveTo = null;
		this.owner = owner.trim();
		this.changeReason = changeReason.trim();
		this.updatedBy = actorId;
		this.updatedAt = Instant.now();
	}

	void publish(UUID actorId) {
		this.status = "PUBLISHED";
		this.publishedBy = actorId;
		this.publishedAt = Instant.now();
		this.updatedBy = actorId;
		this.updatedAt = this.publishedAt;
	}

	void inactivate(UUID actorId, LocalDate date) {
		this.status = "INACTIVE";
		this.effectiveTo = date.isBefore(effectiveFrom) ? effectiveFrom : date;
		this.updatedBy = actorId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getRoutingNumber() { return routingNumber; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getMaterialUnit() { return materialUnit; }
	String getUsageType() { return usageType; }
	String getVersionCode() { return versionCode; }
	BigDecimal getBaseQuantity() { return baseQuantity; }
	LocalDate getEffectiveFrom() { return effectiveFrom; }
	LocalDate getEffectiveTo() { return effectiveTo; }
	String getOwner() { return owner; }
	String getChangeReason() { return changeReason; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getUpdatedAt() { return updatedAt; }
	Instant getPublishedAt() { return publishedAt; }
}

@Entity
@Table(schema = "product", name = "routing_operations")
class RoutingOperationEntity {
	@Id private UUID id;
	@Column(name = "routing_id") private UUID routingId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "sequence_number") private int sequenceNumber;
	@Column(name = "operation_code") private String operationCode;
	@Column(name = "operation_name") private String operationName;
	@Column(name = "work_center_code") private String workCenterCode;
	@Column(name = "work_center_name") private String workCenterName;
	@Column(name = "setup_minutes") private BigDecimal setupMinutes;
	@Column(name = "run_minutes_per_unit") private BigDecimal runMinutesPerUnit;
	@Column(name = "queue_minutes") private BigDecimal queueMinutes;
	@Column(name = "inspection_required") private boolean inspectionRequired;
	@Column(name = "instruction_summary") private String instructionSummary;

	protected RoutingOperationEntity() { }

	RoutingOperationEntity(UUID routingId, UUID tenantId, int sequenceNumber, String operationCode,
			String operationName, String workCenterCode, String workCenterName, BigDecimal setupMinutes,
			BigDecimal runMinutesPerUnit, BigDecimal queueMinutes, boolean inspectionRequired,
			String instructionSummary) {
		this.id = UUID.randomUUID();
		this.routingId = routingId;
		this.tenantOrganizationId = tenantId;
		this.sequenceNumber = sequenceNumber;
		this.operationCode = operationCode.trim();
		this.operationName = operationName.trim();
		this.workCenterCode = workCenterCode.trim();
		this.workCenterName = workCenterName.trim();
		this.setupMinutes = setupMinutes;
		this.runMinutesPerUnit = runMinutesPerUnit;
		this.queueMinutes = queueMinutes;
		this.inspectionRequired = inspectionRequired;
		this.instructionSummary = instructionSummary == null || instructionSummary.isBlank() ? null : instructionSummary.trim();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	int getSequenceNumber() { return sequenceNumber; }
	String getOperationCode() { return operationCode; }
	String getOperationName() { return operationName; }
	String getWorkCenterCode() { return workCenterCode; }
	String getWorkCenterName() { return workCenterName; }
	BigDecimal getSetupMinutes() { return setupMinutes; }
	BigDecimal getRunMinutesPerUnit() { return runMinutesPerUnit; }
	BigDecimal getQueueMinutes() { return queueMinutes; }
	boolean isInspectionRequired() { return inspectionRequired; }
	String getInstructionSummary() { return instructionSummary; }
}

@Entity
@Table(schema = "product", name = "routing_events")
class RoutingEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "routing_id") private UUID routingId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected RoutingEventEntity() { }

	RoutingEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID routingId, String action,
			String fromStatus, String toStatus, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorId;
		this.routingId = routingId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	String getAction() { return action; }
	String getFromStatus() { return fromStatus; }
	String getToStatus() { return toStatus; }
	String getRequestId() { return requestId; }
	Map<String, Object> getDetails() { return details; }
	Instant getOccurredAt() { return occurredAt; }
}
