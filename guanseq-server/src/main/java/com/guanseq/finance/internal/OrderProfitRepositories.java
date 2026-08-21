package com.guanseq.finance.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderProfitSettlementRepository extends JpaRepository<OrderProfitSettlementEntity, UUID> {
	@Query("""
			select s from OrderProfitSettlementEntity s
			where s.tenantOrganizationId = :tenantId
			and (:costStatus = '' or s.costStatus = :costStatus)
			and (:query = ''
				or lower(s.settlementNumber) like lower(concat('%', :query, '%'))
				or lower(s.orderNumber) like lower(concat('%', :query, '%'))
				or lower(s.customerCode) like lower(concat('%', :query, '%'))
				or lower(s.customerName) like lower(concat('%', :query, '%')))
			""")
	Page<OrderProfitSettlementEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("costStatus") String costStatus, Pageable pageable);

	Optional<OrderProfitSettlementEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
	Optional<OrderProfitSettlementEntity> findByTenantOrganizationIdAndSalesOrderId(UUID tenantOrganizationId, UUID salesOrderId);
	Optional<OrderProfitSettlementEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
	List<OrderProfitSettlementEntity> findByTenantOrganizationIdOrderBySettledAtDesc(UUID tenantOrganizationId);
}

interface ItemStandardCostRepository extends JpaRepository<ItemStandardCostEntity, UUID> {
	Optional<ItemStandardCostEntity> findFirstByTenantOrganizationIdAndMaterialIdAndStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
			UUID tenantOrganizationId, UUID materialId, String status, LocalDate effectiveDate);
}

interface OrderProfitEventRepository extends JpaRepository<OrderProfitEventEntity, UUID> { }
