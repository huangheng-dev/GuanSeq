package com.guanseq.planning.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.guanseq.masterdata.api.MasterDataReferenceProvider.MaterialReference;

@Entity
@Table(schema = "planning", name = "material_planning_parameters")
class MaterialPlanningParameterEntity {
	@Id @Column(name = "material_id") private UUID materialId;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "material_code") private String materialCode;
	@Column(name = "material_name") private String materialName;
	@Column(name = "material_specification") private String materialSpecification;
	@Column(name = "procurement_type") private String procurementType;
	private String unit;
	@Column(name = "lead_time_days") private int leadTimeDays;
	@Version private long version;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected MaterialPlanningParameterEntity() { }
	MaterialPlanningParameterEntity(UUID tenantId, UUID organizationId, MaterialReference material, int days, UUID actorId) {
		this.materialId = material.id(); this.tenantOrganizationId = tenantId; this.owningOrganizationId = organizationId;
		this.materialCode = material.code(); this.materialName = material.name(); this.materialSpecification = material.specification();
		this.procurementType = material.procurementType(); this.unit = material.baseUnit(); this.leadTimeDays = days;
		this.createdBy = actorId; this.createdAt = Instant.now(); this.updatedBy = actorId; this.updatedAt = createdAt;
	}
	void update(int days, UUID actorId) { this.leadTimeDays = days; this.updatedBy = actorId; this.updatedAt = Instant.now(); }
	UUID getMaterialId() { return materialId; } UUID getTenantOrganizationId() { return tenantOrganizationId; }
	String getMaterialCode() { return materialCode; } String getMaterialName() { return materialName; }
	String getMaterialSpecification() { return materialSpecification; } String getProcurementType() { return procurementType; }
	String getUnit() { return unit; } int getLeadTimeDays() { return leadTimeDays; } long getVersion() { return version; }
	Instant getUpdatedAt() { return updatedAt; }
}
