package com.guanseq.sales.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SalesOrderRepository extends JpaRepository<SalesOrderEntity, UUID> {

	@Query("""
			select o from SalesOrderEntity o
			where o.tenantOrganizationId = :tenantId
			and (:status = '' or o.status = :status)
			and (:query = ''
				or lower(o.orderNumber) like lower(concat('%', :query, '%'))
				or lower(o.customerCode) like lower(concat('%', :query, '%'))
				or lower(o.customerName) like lower(concat('%', :query, '%')))
			""")
	Page<SalesOrderEntity> search(
			@Param("tenantId") UUID tenantId,
			@Param("query") String query,
			@Param("status") String status,
			Pageable pageable);

	Optional<SalesOrderEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);

	List<SalesOrderEntity> findByTenantOrganizationIdAndStatusIn(UUID tenantOrganizationId, List<String> statuses, Sort sort);
}

interface SalesOrderChangeEventRepository extends JpaRepository<SalesOrderChangeEventEntity, UUID> {
}
