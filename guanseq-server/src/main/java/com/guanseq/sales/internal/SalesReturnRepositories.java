package com.guanseq.sales.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SalesReturnRepository extends JpaRepository<SalesReturnEntity, UUID> {
	@Query("""
			select r from SalesReturnEntity r
			where r.tenantOrganizationId = :tenantId
			and (:status = '' or r.status = :status)
			and (:query = ''
				or lower(r.returnNumber) like lower(concat('%', :query, '%'))
				or lower(r.orderNumber) like lower(concat('%', :query, '%'))
				or lower(r.customerCode) like lower(concat('%', :query, '%'))
				or lower(r.customerName) like lower(concat('%', :query, '%')))
			""")
	Page<SalesReturnEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<SalesReturnEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
	Optional<SalesReturnEntity> findByTenantOrganizationIdAndCreateRequestId(UUID tenantOrganizationId, String requestId);
}

interface SalesReturnLineRepository extends JpaRepository<SalesReturnLineEntity, UUID> {
	@Query("""
			select coalesce(sum(line.authorizedQuantity), 0) from SalesReturnLineEntity line
			join line.salesReturn r
			where r.tenantOrganizationId = :tenantId
			and line.orderLineId = :orderLineId
			and r.status = 'PENDING_RECEIPT'
			""")
	BigDecimal sumPendingQuantity(@Param("tenantId") UUID tenantId, @Param("orderLineId") UUID orderLineId);
}

interface SalesReturnEventRepository extends JpaRepository<SalesReturnEventEntity, UUID> {
	List<SalesReturnEventEntity> findByReturnIdOrderByOccurredAtDesc(UUID returnId);
	Optional<SalesReturnEventEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
}
