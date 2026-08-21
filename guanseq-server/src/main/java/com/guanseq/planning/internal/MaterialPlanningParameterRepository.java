package com.guanseq.planning.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MaterialPlanningParameterRepository extends JpaRepository<MaterialPlanningParameterEntity, UUID> {
	Optional<MaterialPlanningParameterEntity> findByMaterialIdAndTenantOrganizationId(UUID materialId, UUID tenantId);
	List<MaterialPlanningParameterEntity> findByTenantOrganizationIdAndMaterialIdIn(UUID tenantId, Collection<UUID> materialIds);
}
