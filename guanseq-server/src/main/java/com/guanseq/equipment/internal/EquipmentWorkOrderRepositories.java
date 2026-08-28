package com.guanseq.equipment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentWorkOrderRepository extends JpaRepository<EquipmentWorkOrderEntity, UUID> {
	@Query("""
			select workOrder from EquipmentWorkOrderEntity workOrder
			where workOrder.tenantOrganizationId = :tenantId and workOrder.workspaceId = :workspaceId
			and (:type = '' or workOrder.workType = :type)
			and (:status = '' or workOrder.status = :status)
			and (:query = ''
				or lower(workOrder.workOrderNumber) like lower(concat('%', :query, '%'))
				or lower(workOrder.assetCodeSnapshot) like lower(concat('%', :query, '%'))
				or lower(workOrder.assetNameSnapshot) like lower(concat('%', :query, '%'))
				or lower(workOrder.title) like lower(concat('%', :query, '%'))
				or lower(workOrder.assignee) like lower(concat('%', :query, '%')))
			""")
	Page<EquipmentWorkOrderEntity> search(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId,
			@Param("query") String query, @Param("type") String type, @Param("status") String status,
			Pageable pageable);

	Optional<EquipmentWorkOrderEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId,
			UUID workspaceId);
	Optional<EquipmentWorkOrderEntity> findByTenantOrganizationIdAndCreationRequestId(UUID tenantId, String requestId);
	Optional<EquipmentWorkOrderEntity> findBySourcePlanIdAndSourceDueDate(UUID sourcePlanId, LocalDate sourceDueDate);
	long countBySourcePlanIdAndStatusNotInAndDueAtBefore(UUID sourcePlanId, List<String> statuses, Instant dueAt);
	List<EquipmentWorkOrderEntity> findBySourcePlanIdAndStatusNotInAndDueAtBeforeOrderByDueAtAsc(UUID sourcePlanId,
			List<String> statuses, Instant dueAt, Pageable pageable);

	@Query("""
			select workOrder from EquipmentWorkOrderEntity workOrder
			where workOrder.assetId = :assetId and workOrder.workType = 'REPAIR'
			and workOrder.status not in ('COMPLETED', 'CANCELLED')
			order by workOrder.createdAt asc
			""")
	List<EquipmentWorkOrderEntity> findOpenRepairs(@Param("assetId") UUID assetId, Pageable pageable);
}

interface EquipmentWorkOrderEventRepository extends JpaRepository<EquipmentWorkOrderEventEntity, UUID> {
	List<EquipmentWorkOrderEventEntity> findByWorkOrderIdOrderByOccurredAtDesc(UUID workOrderId);
	Optional<EquipmentWorkOrderEventEntity> findByWorkOrderIdAndRequestId(UUID workOrderId, String requestId);
}
