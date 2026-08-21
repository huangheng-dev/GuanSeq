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

interface BomRepository extends JpaRepository<BomEntity, UUID> {

	@Query("""
			select b from BomEntity b
			where b.tenantOrganizationId = :tenantId
			and (:status = '' or b.status = :status)
			and (:query = ''
				or lower(b.bomNumber) like lower(concat('%', :query, '%'))
				or lower(b.parentMaterialCode) like lower(concat('%', :query, '%'))
				or lower(b.parentMaterialName) like lower(concat('%', :query, '%'))
				or lower(b.versionCode) like lower(concat('%', :query, '%')))
			""")
	Page<BomEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<BomEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);

	boolean existsByTenantOrganizationIdAndParentMaterialIdAndUsageTypeAndStatusAndIdNot(
			UUID tenantOrganizationId, UUID parentMaterialId, String usageType, String status, UUID id);

	@Query("""
			select b from BomEntity b
			where b.tenantOrganizationId = :tenantId
			and b.parentMaterialId = :parentMaterialId
			and b.status = 'PUBLISHED'
			and b.effectiveFrom <= :effectiveDate
			and (b.effectiveTo is null or b.effectiveTo >= :effectiveDate)
			""")
	Optional<BomEntity> findEffectivePublished(@Param("tenantId") UUID tenantId,
			@Param("parentMaterialId") UUID parentMaterialId, @Param("effectiveDate") LocalDate effectiveDate);
}

interface BomLineRepository extends JpaRepository<BomLineEntity, UUID> {

	List<BomLineEntity> findByBomIdOrderByLineNumberAsc(UUID bomId);

	@Modifying
	void deleteByBomId(UUID bomId);
}

interface BomEventRepository extends JpaRepository<BomEventEntity, UUID> {

	List<BomEventEntity> findByBomIdOrderByOccurredAtDesc(UUID bomId);
}
