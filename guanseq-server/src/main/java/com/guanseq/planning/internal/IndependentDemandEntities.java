package com.guanseq.planning.internal;

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
@Table(schema = "planning", name = "independent_demands")
class IndependentDemandEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "demand_number") private String demandNumber;
	@Column(name = "source_type") private String sourceType;
	@Column(name = "source_id") private UUID sourceId;
	@Column(name = "source_number") private String sourceNumber;
	@Column(name = "source_line_id") private UUID sourceLineId;
	@Column(name = "source_line_number") private Integer sourceLineNumber;
	@Column(name = "source_customer") private String sourceCustomer;
	@Column(name = "material_id") private UUID materialId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	private String unit;
	private BigDecimal quantity;
	@Column(name = "required_date") private LocalDate requiredDate;
	private String priority;
	private String owner;
	private String status;
	private String note;
	@Column(name = "cancellation_reason") private String cancellationReason;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected IndependentDemandEntity() {
	}

	static IndependentDemandEntity manual(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String demandNumber, UUID actorUserId) {
		return new IndependentDemandEntity(tenantOrganizationId, owningOrganizationId, workspaceId, demandNumber,
				"MANUAL", null, null, null, null, null, "DRAFT", actorUserId);
	}

	static IndependentDemandEntity salesOrder(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String demandNumber, UUID actorUserId, UUID sourceId, String sourceNumber, UUID sourceLineId,
			int sourceLineNumber, String sourceCustomer) {
		return new IndependentDemandEntity(tenantOrganizationId, owningOrganizationId, workspaceId, demandNumber,
				"SALES_ORDER", sourceId, sourceNumber, sourceLineId, sourceLineNumber, sourceCustomer, "ACTIVE", actorUserId);
	}

	private IndependentDemandEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId,
			String demandNumber, String sourceType, UUID sourceId, String sourceNumber, UUID sourceLineId,
			Integer sourceLineNumber, String sourceCustomer, String status, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.demandNumber = demandNumber;
		this.sourceType = sourceType;
		this.sourceId = sourceId;
		this.sourceNumber = sourceNumber;
		this.sourceLineId = sourceLineId;
		this.sourceLineNumber = sourceLineNumber;
		this.sourceCustomer = sourceCustomer;
		this.status = status;
		this.priority = "NORMAL";
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void update(UUID materialId, String materialCode, String materialName, String materialSpecification,
			String unit, BigDecimal quantity, LocalDate requiredDate, String priority, String owner, String note,
			UUID actorUserId) {
		this.materialId = materialId;
		this.materialCode = materialCode;
		this.materialName = materialName;
		this.materialSpecification = materialSpecification;
		this.unit = unit;
		this.quantity = quantity;
		this.requiredDate = requiredDate;
		this.priority = priority;
		this.owner = owner.trim();
		this.note = note == null || note.isBlank() ? null : note.trim();
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void transition(String targetStatus, String comment, UUID actorUserId) {
		this.status = targetStatus;
		this.cancellationReason = "CANCELLED".equals(targetStatus) ? comment : null;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getDemandNumber() { return demandNumber; }
	String getSourceType() { return sourceType; }
	UUID getSourceId() { return sourceId; }
	String getSourceNumber() { return sourceNumber; }
	UUID getSourceLineId() { return sourceLineId; }
	Integer getSourceLineNumber() { return sourceLineNumber; }
	String getSourceCustomer() { return sourceCustomer; }
	UUID getMaterialId() { return materialId; }
	String getMaterialCode() { return materialCode; }
	String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getQuantity() { return quantity; }
	LocalDate getRequiredDate() { return requiredDate; }
	String getPriority() { return priority; }
	String getOwner() { return owner; }
	String getStatus() { return status; }
	String getNote() { return note; }
	String getCancellationReason() { return cancellationReason; }
	long getVersion() { return version; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "planning", name = "demand_events")
class IndependentDemandEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "demand_id") private UUID demandId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	private String comment;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected IndependentDemandEventEntity() {
	}

	IndependentDemandEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID demandId,
			String action, String fromStatus, String toStatus, String requestId, String comment,
			Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.demandId = demandId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.requestId = requestId;
		this.comment = comment;
		this.details = details;
		this.occurredAt = Instant.now();
	}
}
