package com.guanseq.procurement.internal;

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

interface SupplierRepository extends JpaRepository<SupplierEntity, UUID> {
	List<SupplierEntity> findByTenantOrganizationIdAndStatusOrderByCodeAsc(UUID tenantId, String status);
	Optional<SupplierEntity> findByIdAndTenantOrganizationIdAndStatus(UUID id, UUID tenantId, String status);
}

interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, UUID> {
	@Query("""
		select distinct o from PurchaseOrderEntity o join fetch o.lines l
		where o.tenantOrganizationId = :tenantId and l.receivedQuantity > 0
		order by o.orderNumber asc
		""")
	List<PurchaseOrderEntity> findReceivedOrders(@Param("tenantId") UUID tenantId);

	@Query("""
		select distinct o from PurchaseOrderEntity o join fetch o.lines l
		where o.id = :id and o.tenantOrganizationId = :tenantId
		and exists (select 1 from PurchaseOrderLineEntity receivedLine
			where receivedLine.order = o and receivedLine.receivedQuantity > 0)
		""")
	Optional<PurchaseOrderEntity> findReceivedOrder(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

	@Query("""
		select o from PurchaseOrderEntity o where o.tenantOrganizationId = :tenantId
		and (:status = '' or o.status = :status)
		and (:query = '' or lower(o.orderNumber) like lower(concat('%', :query, '%'))
			or lower(o.supplierCode) like lower(concat('%', :query, '%'))
			or lower(o.supplierName) like lower(concat('%', :query, '%')))
		""")
	Page<PurchaseOrderEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<PurchaseOrderEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	List<PurchaseOrderEntity> findByTenantOrganizationIdAndStatus(UUID tenantId, String status);
	Optional<PurchaseOrderEntity> findByTenantOrganizationIdAndSourceTypeAndSourceId(UUID tenantId, String sourceType, UUID sourceId);

	@Query("""
		select distinct o from PurchaseOrderEntity o join fetch o.lines l
		where o.tenantOrganizationId = :tenantId and o.status = 'RELEASED'
		and o.promisedReceiptDate <= :horizonEnd and l.materialId in :materialIds
		and l.receivedQuantity < l.orderedQuantity
		""")
	List<PurchaseOrderEntity> findReleasedReceipts(@Param("tenantId") UUID tenantId,
			@Param("materialIds") Collection<UUID> materialIds, @Param("horizonEnd") LocalDate horizonEnd);
}

interface PurchaseOrderEventRepository extends JpaRepository<PurchaseOrderEventEntity, UUID> { }
