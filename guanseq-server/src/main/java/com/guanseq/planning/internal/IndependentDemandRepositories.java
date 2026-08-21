package com.guanseq.planning.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IndependentDemandRepository extends JpaRepository<IndependentDemandEntity, UUID> {

	@Query("""
			select d from IndependentDemandEntity d
			where d.tenantOrganizationId = :tenantId
			and (:status = '' or d.status = :status)
			and (:sourceType = '' or d.sourceType = :sourceType)
			and (:query = ''
				or lower(d.demandNumber) like lower(concat('%', :query, '%'))
				or lower(d.materialCode) like lower(concat('%', :query, '%'))
				or lower(d.materialName) like lower(concat('%', :query, '%'))
				or lower(coalesce(d.sourceNumber, '')) like lower(concat('%', :query, '%')))
			""")
	Page<IndependentDemandEntity> search(
			@Param("tenantId") UUID tenantId,
			@Param("query") String query,
			@Param("status") String status,
			@Param("sourceType") String sourceType,
			Pageable pageable);

	Optional<IndependentDemandEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);

	boolean existsByTenantOrganizationIdAndSourceTypeAndSourceLineId(UUID tenantOrganizationId, String sourceType, UUID sourceLineId);

	List<IndependentDemandEntity> findByTenantOrganizationIdAndStatusAndRequiredDateBetweenOrderByRequiredDateAscDemandNumberAsc(
			UUID tenantOrganizationId, String status, LocalDate horizonStart, LocalDate horizonEnd);
}

interface IndependentDemandEventRepository extends JpaRepository<IndependentDemandEventEntity, UUID> {
}
