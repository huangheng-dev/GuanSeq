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
@Table(schema = "product", name = "boms")
class BomEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "bom_number") private String bomNumber;
	@Column(name = "parent_material_id") private UUID parentMaterialId;
	@Column(name = "parent_material_code") private String parentMaterialCode;
	@Column(name = "parent_material_name") private String parentMaterialName;
	@Column(name = "parent_material_specification") private String parentMaterialSpecification;
	@Column(name = "parent_unit") private String parentUnit;
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

	protected BomEntity() {
	}

	BomEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, String bomNumber,
			UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.workspaceId = workspaceId;
		this.bomNumber = bomNumber;
		this.status = "DRAFT";
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void updateDraft(UUID parentMaterialId, String parentMaterialCode, String parentMaterialName,
			String parentMaterialSpecification, String parentUnit, String usageType, String versionCode,
			BigDecimal baseQuantity, LocalDate effectiveFrom, String owner, String changeReason,
			UUID actorUserId) {
		this.parentMaterialId = parentMaterialId;
		this.parentMaterialCode = parentMaterialCode;
		this.parentMaterialName = parentMaterialName;
		this.parentMaterialSpecification = parentMaterialSpecification;
		this.parentUnit = parentUnit;
		this.usageType = usageType;
		this.versionCode = versionCode.trim();
		this.baseQuantity = baseQuantity;
		this.effectiveFrom = effectiveFrom;
		this.effectiveTo = null;
		this.owner = owner.trim();
		this.changeReason = changeReason.trim();
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void publish(UUID actorUserId) {
		this.status = "PUBLISHED";
		this.publishedBy = actorUserId;
		this.publishedAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.publishedAt;
	}

	void inactivate(UUID actorUserId, LocalDate date) {
		this.status = "INACTIVE";
		this.effectiveTo = date.isBefore(effectiveFrom) ? effectiveFrom : date;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getBomNumber() { return bomNumber; }
	UUID getParentMaterialId() { return parentMaterialId; }
	String getParentMaterialCode() { return parentMaterialCode; }
	String getParentMaterialName() { return parentMaterialName; }
	String getParentMaterialSpecification() { return parentMaterialSpecification; }
	String getParentUnit() { return parentUnit; }
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
@Table(schema = "product", name = "bom_lines")
class BomLineEntity {

	@Id private UUID id;
	@Column(name = "bom_id") private UUID bomId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "line_number") private int lineNumber;
	@Column(name = "component_material_id") private UUID componentMaterialId;
	@Column(name = "component_material_code") private String componentMaterialCode;
	@Column(name = "component_material_name") private String componentMaterialName;
	@Column(name = "component_material_specification") private String componentMaterialSpecification;
	private String unit;
	private BigDecimal quantity;
	@Column(name = "scrap_rate") private BigDecimal scrapRate;
	private String note;

	protected BomLineEntity() {
	}

	BomLineEntity(UUID bomId, UUID tenantOrganizationId, int lineNumber, UUID componentMaterialId,
			String componentMaterialCode, String componentMaterialName, String componentMaterialSpecification,
			String unit, BigDecimal quantity, BigDecimal scrapRate, String note) {
		this.id = UUID.randomUUID();
		this.bomId = bomId;
		this.tenantOrganizationId = tenantOrganizationId;
		this.lineNumber = lineNumber;
		this.componentMaterialId = componentMaterialId;
		this.componentMaterialCode = componentMaterialCode;
		this.componentMaterialName = componentMaterialName;
		this.componentMaterialSpecification = componentMaterialSpecification;
		this.unit = unit;
		this.quantity = quantity;
		this.scrapRate = scrapRate;
		this.note = note == null || note.isBlank() ? null : note.trim();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	int getLineNumber() { return lineNumber; }
	UUID getComponentMaterialId() { return componentMaterialId; }
	String getComponentMaterialCode() { return componentMaterialCode; }
	String getComponentMaterialName() { return componentMaterialName; }
	String getComponentMaterialSpecification() { return componentMaterialSpecification; }
	String getUnit() { return unit; }
	BigDecimal getQuantity() { return quantity; }
	BigDecimal getScrapRate() { return scrapRate; }
	String getNote() { return note; }
}

@Entity
@Table(schema = "product", name = "bom_events")
class BomEventEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "bom_id") private UUID bomId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected BomEventEntity() {
	}

	BomEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID bomId, String action,
			String fromStatus, String toStatus, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.bomId = bomId;
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
