package com.guanseq.quality.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FinalInspectionRepository extends JpaRepository<FinalInspectionEntity, UUID> {
	@Query("""
		select i from FinalInspectionEntity i where i.tenantOrganizationId = :tenantId
		and i.inspectionType = :type
		and (:status = '' or i.status = :status)
		and (:query = '' or lower(i.inspectionNumber) like lower(concat('%', :query, '%'))
			or lower(i.sourceNumber) like lower(concat('%', :query, '%'))
			or lower(i.orderNumber) like lower(concat('%', :query, '%'))
			or lower(i.materialCode) like lower(concat('%', :query, '%'))
			or lower(i.materialName) like lower(concat('%', :query, '%')))
		""")
	Page<FinalInspectionEntity> search(@Param("tenantId") UUID tenantId, @Param("type") String type, @Param("query") String query,
			@Param("status") String status, Pageable pageable);
	Optional<FinalInspectionEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	Optional<FinalInspectionEntity> findByTenantOrganizationIdAndSourceTypeAndSourceId(UUID tenantId, String sourceType,
			UUID sourceId);
	Optional<FinalInspectionEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
	Optional<FinalInspectionEntity> findByTenantOrganizationIdAndDecisionRequestId(UUID tenantId, String requestId);
}

interface FinalInspectionEventRepository extends JpaRepository<FinalInspectionEventEntity, UUID> { }
