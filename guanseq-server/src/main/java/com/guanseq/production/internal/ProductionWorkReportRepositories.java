package com.guanseq.production.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductionWorkReportRepository extends JpaRepository<ProductionWorkReportEntity, UUID> {
	@Query("""
		select r from ProductionWorkReportEntity r where r.tenantOrganizationId = :tenantId
		and (:status = '' or r.status = :status)
		and (:query = '' or lower(r.reportNumber) like lower(concat('%', :query, '%'))
			or lower(r.orderNumber) like lower(concat('%', :query, '%'))
			or lower(r.materialCode) like lower(concat('%', :query, '%'))
			or lower(r.materialName) like lower(concat('%', :query, '%'))
			or lower(r.operatorName) like lower(concat('%', :query, '%')))
		""")
	Page<ProductionWorkReportEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);
	Optional<ProductionWorkReportEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	Optional<ProductionWorkReportEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
	Optional<ProductionWorkReportEntity> findByTenantOrganizationIdAndSettlementRequestId(UUID tenantId, String requestId);
}
