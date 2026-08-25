package com.guanseq.equipment.internal;

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
@Table(schema = "equipment", name = "assets")
class EquipmentAssetEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "asset_code") private String assetCode;
	@Column(name = "asset_name") private String assetName;
	private String category;
	private String manufacturer;
	private String model;
	@Column(name = "serial_number") private String serialNumber;
	@Column(name = "work_center_code") private String workCenterCode;
	@Column(name = "work_center_name") private String workCenterName;
	private String location;
	@Column(name = "responsible_person") private String responsiblePerson;
	@Column(name = "commissioning_date") private LocalDate commissioningDate;
	@Column(name = "operating_status") private String operatingStatus;
	@Column(name = "status_changed_at") private Instant statusChangedAt;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentAssetEntity() { }

	EquipmentAssetEntity(UUID tenantId, UUID organizationId, UUID workspaceId, String assetCode, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId;
		this.assetCode = assetCode.trim().toUpperCase();
		this.operatingStatus = "IDLE";
		this.statusChangedAt = Instant.now();
		this.createdBy = actorId;
		this.createdAt = this.statusChangedAt;
		this.updatedBy = actorId;
		this.updatedAt = this.createdAt;
	}

	void updateDetails(String assetName, String category, String manufacturer, String model, String serialNumber,
			String workCenterCode, String workCenterName, String location, String responsiblePerson,
			LocalDate commissioningDate, UUID actorId) {
		this.assetName = assetName.trim();
		this.category = category;
		this.manufacturer = nullable(manufacturer);
		this.model = nullable(model);
		this.serialNumber = nullable(serialNumber);
		this.workCenterCode = upperNullable(workCenterCode);
		this.workCenterName = nullable(workCenterName);
		this.location = location.trim();
		this.responsiblePerson = responsiblePerson.trim();
		this.commissioningDate = commissioningDate;
		this.updatedBy = actorId;
		this.updatedAt = Instant.now();
	}

	void changeStatus(String status, UUID actorId) {
		this.operatingStatus = status;
		this.statusChangedAt = Instant.now();
		this.updatedBy = actorId;
		this.updatedAt = this.statusChangedAt;
	}

	private static String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private static String upperNullable(String value) { String normalized = nullable(value); return normalized == null ? null : normalized.toUpperCase(); }

	UUID getId() { return id; }
	String getAssetCode() { return assetCode; }
	String getAssetName() { return assetName; }
	String getCategory() { return category; }
	String getManufacturer() { return manufacturer; }
	String getModel() { return model; }
	String getSerialNumber() { return serialNumber; }
	String getWorkCenterCode() { return workCenterCode; }
	String getWorkCenterName() { return workCenterName; }
	String getLocation() { return location; }
	String getResponsiblePerson() { return responsiblePerson; }
	LocalDate getCommissioningDate() { return commissioningDate; }
	String getOperatingStatus() { return operatingStatus; }
	Instant getStatusChangedAt() { return statusChangedAt; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
}

@Entity
@Table(schema = "equipment", name = "asset_events")
class EquipmentAssetEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "asset_id") private UUID assetId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected EquipmentAssetEventEntity() { }

	EquipmentAssetEventEntity(UUID tenantId, UUID workspaceId, UUID actorId, UUID assetId, String action,
			String fromStatus, String toStatus, String reason, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorId;
		this.assetId = assetId;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason.trim();
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getActorUserId() { return actorUserId; }
	String getAction() { return action; }
	String getFromStatus() { return fromStatus; }
	String getToStatus() { return toStatus; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	Map<String, Object> getDetails() { return details; }
	Instant getOccurredAt() { return occurredAt; }
}
