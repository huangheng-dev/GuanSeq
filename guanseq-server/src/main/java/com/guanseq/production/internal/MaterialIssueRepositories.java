package com.guanseq.production.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MaterialIssueRepository extends JpaRepository<MaterialIssueEntity, UUID> {
	@Query("""
			select i from MaterialIssueEntity i where i.tenantOrganizationId = :tenantId
			and (:status = '' or i.status = :status)
			and (:query = ''
				or lower(i.issueNumber) like lower(concat('%', :query, '%'))
				or lower(i.orderNumber) like lower(concat('%', :query, '%'))
				or lower(i.materialCode) like lower(concat('%', :query, '%'))
				or lower(i.materialName) like lower(concat('%', :query, '%'))
				or lower(i.warehouseCode) like lower(concat('%', :query, '%')))
			""")
	Page<MaterialIssueEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<MaterialIssueEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	Optional<MaterialIssueEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
	boolean existsByTenantOrganizationIdAndProductionOrderIdAndStatusNot(UUID tenantId, UUID productionOrderId, String status);
	List<MaterialIssueEntity> findByTenantOrganizationIdAndProductionOrderIdAndStatusNot(UUID tenantId, UUID productionOrderId, String status);
}

interface MaterialIssueLineRepository extends JpaRepository<MaterialIssueLineEntity, UUID> {
	List<MaterialIssueLineEntity> findByTenantOrganizationIdAndIssueIdOrderByLineNumberAsc(UUID tenantId, UUID issueId);
	Optional<MaterialIssueLineEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
}

interface MaterialIssueEventRepository extends JpaRepository<MaterialIssueEventEntity, UUID> {
	List<MaterialIssueEventEntity> findByIssueIdOrderByOccurredAtDesc(UUID issueId);
	Optional<MaterialIssueEventEntity> findFirstByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
}

interface MaterialReturnRepository extends JpaRepository<MaterialReturnEntity, UUID> {
	List<MaterialReturnEntity> findByTenantOrganizationIdAndIssueIdOrderByCreatedAtDesc(UUID tenantId, UUID issueId);
	Optional<MaterialReturnEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
}

interface MaterialReturnLineRepository extends JpaRepository<MaterialReturnLineEntity, UUID> {
	List<MaterialReturnLineEntity> findByReturnIdOrderByLineNumberAsc(UUID returnId);
}

interface MaterialStockTransactionRepository extends JpaRepository<MaterialStockTransactionEntity, UUID> {
	List<MaterialStockTransactionEntity> findByTenantOrganizationIdAndIssueIdOrderByOccurredAtDesc(UUID tenantId, UUID issueId);
}


