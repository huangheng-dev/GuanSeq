package com.guanseq.product.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoutingRepository extends JpaRepository<RoutingEntity, UUID> {
	@Query("""
			select r from RoutingEntity r
			where r.tenantOrganizationId = :tenantId
			and (:status = '' or r.status = :status)
			and (:query = ''
				or lower(r.routingNumber) like lower(concat('%', :query, '%'))
				or lower(r.materialCode) like lower(concat('%', :query, '%'))
				or lower(r.materialName) like lower(concat('%', :query, '%'))
				or lower(r.versionCode) like lower(concat('%', :query, '%')))
			""")
	Page<RoutingEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<RoutingEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);

	boolean existsByTenantOrganizationIdAndMaterialIdAndUsageTypeAndStatusAndIdNot(
			UUID tenantOrganizationId, UUID materialId, String usageType, String status, UUID id);

	@Query("""
			select r from RoutingEntity r where r.tenantOrganizationId = :tenantId
			and r.materialId = :materialId and r.status = 'PUBLISHED'
			and r.effectiveFrom <= :effectiveDate
			and (r.effectiveTo is null or r.effectiveTo >= :effectiveDate)
			""")
	Optional<RoutingEntity> findEffectivePublished(@Param("tenantId") UUID tenantId,
			@Param("materialId") UUID materialId, @Param("effectiveDate") LocalDate effectiveDate);
}

interface RoutingOperationRepository extends JpaRepository<RoutingOperationEntity, UUID> {
	List<RoutingOperationEntity> findByRoutingIdOrderBySequenceNumberAsc(UUID routingId);
	@Modifying void deleteByRoutingId(UUID routingId);
}

interface RoutingEventRepository extends JpaRepository<RoutingEventEntity, UUID> {
	List<RoutingEventEntity> findByRoutingIdOrderByOccurredAtDesc(UUID routingId);
}
