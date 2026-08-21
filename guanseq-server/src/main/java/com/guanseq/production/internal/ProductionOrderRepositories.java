package com.guanseq.production.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductionOrderRepository extends JpaRepository<ProductionOrderEntity, UUID> {
	@Query("""
		select o from ProductionOrderEntity o where o.tenantOrganizationId = :tenantId
		and (:status = '' or o.status = :status)
		and (:query = '' or lower(o.orderNumber) like lower(concat('%', :query, '%'))
			or lower(o.materialCode) like lower(concat('%', :query, '%'))
			or lower(o.materialName) like lower(concat('%', :query, '%'))
			or lower(o.workshop) like lower(concat('%', :query, '%')))
		""")
	Page<ProductionOrderEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<ProductionOrderEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	Optional<ProductionOrderEntity> findByTenantOrganizationIdAndSourceTypeAndSourceId(UUID tenantId, String sourceType, UUID sourceId);
	List<ProductionOrderEntity> findAllByTenantOrganizationIdAndSourceTypeAndSourceId(UUID tenantId, String sourceType, UUID sourceId);

	@Query("""
		select o from ProductionOrderEntity o where o.tenantOrganizationId = :tenantId
		and o.status in ('RELEASED', 'IN_PROGRESS') and o.plannedReceiptDate <= :horizonEnd
		and o.materialId in :materialIds and o.completedQuantity < o.plannedQuantity
		""")
	List<ProductionOrderEntity> findScheduledReceipts(@Param("tenantId") UUID tenantId,
			@Param("materialIds") Collection<UUID> materialIds, @Param("horizonEnd") LocalDate horizonEnd);
}

interface ProductionOrderEventRepository extends JpaRepository<ProductionOrderEventEntity, UUID> { }

