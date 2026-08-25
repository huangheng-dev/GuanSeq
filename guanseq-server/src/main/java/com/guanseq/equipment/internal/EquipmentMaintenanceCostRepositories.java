package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentSparePartRepository extends JpaRepository<EquipmentSparePartEntity, UUID> {
	@Query("""
			select spare from EquipmentSparePartEntity spare
			where spare.tenantOrganizationId = :tenantId and spare.workspaceId = :workspaceId
			and (:query = '' or lower(spare.materialCodeSnapshot) like lower(concat('%', :query, '%'))
				or lower(spare.materialNameSnapshot) like lower(concat('%', :query, '%'))
				or lower(spare.preferredWarehouseCodeSnapshot) like lower(concat('%', :query, '%')))
			order by spare.materialCodeSnapshot asc
			""")
	Page<EquipmentSparePartEntity> search(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId,
			@Param("query") String query, Pageable pageable);

	Optional<EquipmentSparePartEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId, UUID workspaceId);
	Optional<EquipmentSparePartEntity> findByTenantOrganizationIdAndCreationRequestId(UUID tenantId, String requestId);
}

interface MaintenanceSpareTransactionRepository extends JpaRepository<MaintenanceSpareTransactionEntity, UUID> {
	List<MaintenanceSpareTransactionEntity> findByWorkOrderIdOrderByOccurredAtDesc(UUID workOrderId);
	Optional<MaintenanceSpareTransactionEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId,
			UUID workspaceId);
	Optional<MaintenanceSpareTransactionEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);

	@Query("""
			select coalesce(sum(item.quantity), 0) from MaintenanceSpareTransactionEntity item
			where item.returnOfIssueId = :issueId and item.transactionType = 'RETURN'
			""")
	BigDecimal returnedQuantity(@Param("issueId") UUID issueId);
}

interface MaintenanceLaborTransactionRepository extends JpaRepository<MaintenanceLaborTransactionEntity, UUID> {
	List<MaintenanceLaborTransactionEntity> findByWorkOrderIdOrderByOccurredAtDesc(UUID workOrderId);
	Optional<MaintenanceLaborTransactionEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId,
			UUID workspaceId);
	Optional<MaintenanceLaborTransactionEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
	boolean existsByReversalOfEntryId(UUID entryId);
}
