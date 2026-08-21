package com.guanseq.sales.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SalesShipmentRepository extends JpaRepository<SalesShipmentEntity, UUID> {
	@Query("""
			select s from SalesShipmentEntity s
			where s.tenantOrganizationId = :tenantId
			and (:status = '' or s.status = :status)
			and (:query = ''
				or lower(s.shipmentNumber) like lower(concat('%', :query, '%'))
				or lower(s.orderNumber) like lower(concat('%', :query, '%'))
				or lower(s.customerCode) like lower(concat('%', :query, '%'))
				or lower(s.customerName) like lower(concat('%', :query, '%')))
			""")
	Page<SalesShipmentEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<SalesShipmentEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
	Optional<SalesShipmentEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
}

interface SalesShipmentEventRepository extends JpaRepository<SalesShipmentEventEntity, UUID> {
}