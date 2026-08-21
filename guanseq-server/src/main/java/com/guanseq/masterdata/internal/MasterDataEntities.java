package com.guanseq.masterdata.internal;

import java.time.Instant;
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
@Table(schema = "masterdata", name = "customers")
class CustomerEntity {

	@Id
	private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	private String code;
	private String name;
	@Column(name = "customer_type") private String customerType;
	@Column(name = "credit_level") private String creditLevel;
	@Column(name = "contact_name") private String contactName;
	@Column(name = "contact_phone") private String contactPhone;
	private String owner;
	private String status;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected CustomerEntity() {
	}

	CustomerEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.status = "ACTIVE";
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void update(String code, String name, String customerType, String creditLevel, String contactName, String contactPhone, String owner, UUID actorUserId) {
		this.code = code.trim();
		this.name = name.trim();
		this.customerType = customerType;
		this.creditLevel = creditLevel;
		this.contactName = normalize(contactName);
		this.contactPhone = normalize(contactPhone);
		this.owner = owner.trim();
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void changeStatus(String status, UUID actorUserId) {
		this.status = status;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	String getCode() { return code; }
	String getName() { return name; }
	String getCustomerType() { return customerType; }
	String getCreditLevel() { return creditLevel; }
	String getContactName() { return contactName; }
	String getContactPhone() { return contactPhone; }
	String getOwner() { return owner; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getUpdatedAt() { return updatedAt; }

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

@Entity
@Table(schema = "masterdata", name = "materials")
class MaterialEntity {

	@Id
	private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	private String code;
	private String name;
	private String specification;
	@Column(name = "material_type") private String materialType;
	@Column(name = "base_unit") private String baseUnit;
	@Column(name = "procurement_type") private String procurementType;
	@Column(name = "incoming_inspection_required") private boolean incomingInspectionRequired;
	private String owner;
	private String status;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected MaterialEntity() {
	}

	MaterialEntity(UUID tenantOrganizationId, UUID owningOrganizationId, UUID actorUserId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.owningOrganizationId = owningOrganizationId;
		this.status = "ACTIVE";
		this.createdBy = actorUserId;
		this.createdAt = Instant.now();
		this.updatedBy = actorUserId;
		this.updatedAt = this.createdAt;
	}

	void update(String code, String name, String specification, String materialType, String baseUnit, String procurementType, String owner, UUID actorUserId) {
		this.code = code.trim();
		this.name = name.trim();
		this.specification = specification == null || specification.isBlank() ? null : specification.trim();
		this.materialType = materialType;
		this.baseUnit = baseUnit.trim();
		this.procurementType = procurementType;
		this.owner = owner.trim();
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	void changeStatus(String status, UUID actorUserId) {
		this.status = status;
		this.updatedBy = actorUserId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	String getCode() { return code; }
	String getName() { return name; }
	String getSpecification() { return specification; }
	String getMaterialType() { return materialType; }
	String getBaseUnit() { return baseUnit; }
	String getProcurementType() { return procurementType; }
	boolean isIncomingInspectionRequired() { return incomingInspectionRequired; }
	String getOwner() { return owner; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "masterdata", name = "change_events")
class MasterDataChangeEventEntity {

	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "entity_type") private String entityType;
	@Column(name = "entity_id") private UUID entityId;
	private String action;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected MasterDataChangeEventEntity() {
	}

	MasterDataChangeEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, String entityType, UUID entityId, String action, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.entityType = entityType;
		this.entityId = entityId;
		this.action = action;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}
}

