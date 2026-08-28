package com.guanseq.equipment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentMaintenancePlanRepository extends JpaRepository<EquipmentMaintenancePlanEntity, UUID> {
	@Query("""
			select plan from EquipmentMaintenancePlanEntity plan
			where plan.tenantOrganizationId = :tenantId and plan.workspaceId = :workspaceId
			and (:status = '' or plan.status = :status)
			and (:query = '' or lower(plan.planCode) like lower(concat('%', :query, '%'))
				or lower(plan.name) like lower(concat('%', :query, '%'))
				or lower(plan.assetCodeSnapshot) like lower(concat('%', :query, '%'))
				or lower(plan.assetNameSnapshot) like lower(concat('%', :query, '%'))
				or lower(plan.assignee) like lower(concat('%', :query, '%')))
			""")
	Page<EquipmentMaintenancePlanEntity> search(@Param("tenantId") UUID tenantId,
			@Param("workspaceId") UUID workspaceId, @Param("query") String query, @Param("status") String status,
			Pageable pageable);

	Optional<EquipmentMaintenancePlanEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId,
			UUID workspaceId);
	Optional<EquipmentMaintenancePlanEntity> findByTenantOrganizationIdAndCreationRequestId(UUID tenantId,
			String requestId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select plan from EquipmentMaintenancePlanEntity plan
			where plan.tenantOrganizationId = :tenantId and plan.workspaceId = :workspaceId and plan.status = 'ACTIVE'
			order by plan.nextDueDate asc, plan.planCode asc
			""")
	List<EquipmentMaintenancePlanEntity> lockActiveForGeneration(@Param("tenantId") UUID tenantId,
			@Param("workspaceId") UUID workspaceId);

	long countByTenantOrganizationIdAndWorkspaceIdAndStatus(UUID tenantId, UUID workspaceId, String status);
	List<EquipmentMaintenancePlanEntity> findByTenantOrganizationIdAndWorkspaceIdAndStatusOrderByNextDueDateAsc(
			UUID tenantId, UUID workspaceId, String status);
	List<EquipmentMaintenancePlanEntity> findByTenantOrganizationIdAndWorkspaceIdOrderByNextDueDateAsc(
			UUID tenantId, UUID workspaceId);
}

interface EquipmentMaintenancePlanEventRepository extends JpaRepository<EquipmentMaintenancePlanEventEntity, UUID> {
	List<EquipmentMaintenancePlanEventEntity> findByPlanIdOrderByOccurredAtDesc(UUID planId);
	Optional<EquipmentMaintenancePlanEventEntity> findByPlanIdAndRequestId(UUID planId, String requestId);
}

interface EquipmentMaintenanceGenerationRunRepository extends JpaRepository<EquipmentMaintenanceGenerationRunEntity, UUID> {
	Optional<EquipmentMaintenanceGenerationRunEntity> findByTenantOrganizationIdAndWorkspaceIdAndRequestId(UUID tenantId,
			UUID workspaceId, String requestId);
	List<EquipmentMaintenanceGenerationRunEntity> findByTenantOrganizationIdAndWorkspaceIdOrderByStartedAtDesc(
			UUID tenantId, UUID workspaceId, Pageable pageable);
}

interface EquipmentMaintenanceGenerationItemRepository extends JpaRepository<EquipmentMaintenanceGenerationItemEntity, UUID> {
	List<EquipmentMaintenanceGenerationItemEntity> findByRunIdOrderByDueDateAsc(UUID runId);
}
