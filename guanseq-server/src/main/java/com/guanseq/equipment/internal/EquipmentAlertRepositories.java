package com.guanseq.equipment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentAlertRuleRepository extends JpaRepository<EquipmentAlertRuleEntity, UUID> {
	@Query("""
			select rule from EquipmentAlertRuleEntity rule
			where rule.tenantOrganizationId = :tenantId and rule.workspaceId = :workspaceId
			and (:status = '' or rule.status = :status)
			""")
	Page<EquipmentAlertRuleEntity> search(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId,
			@Param("status") String status, Pageable pageable);
	Optional<EquipmentAlertRuleEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId,
			UUID workspaceId);
	Optional<EquipmentAlertRuleEntity> findByTenantOrganizationIdAndCreationRequestId(UUID tenantId, String requestId);
	List<EquipmentAlertRuleEntity> findByConnectionIdAndStatus(UUID connectionId, String status);
}

interface EquipmentAlertRuleEventRepository extends JpaRepository<EquipmentAlertRuleEventEntity, UUID> {
	List<EquipmentAlertRuleEventEntity> findByRuleIdOrderByOccurredAtDesc(UUID ruleId);
	Optional<EquipmentAlertRuleEventEntity> findByRuleIdAndRequestId(UUID ruleId, String requestId);
}

interface EquipmentAlertRepository extends JpaRepository<EquipmentAlertEntity, UUID> {
	@Query("""
			select alert from EquipmentAlertEntity alert
			where alert.tenantOrganizationId = :tenantId and alert.workspaceId = :workspaceId
			and (:status = '' or alert.status = :status)
			and (:severity = '' or alert.severity = :severity)
			""")
	Page<EquipmentAlertEntity> search(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId,
			@Param("status") String status, @Param("severity") String severity, Pageable pageable);
	Optional<EquipmentAlertEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId,
			UUID workspaceId);
	Optional<EquipmentAlertEntity> findByRuleIdAndStatusNot(UUID ruleId, String status);
	long countByTenantOrganizationIdAndWorkspaceIdAndConditionActiveTrue(UUID tenantId, UUID workspaceId);
	long countByTenantOrganizationIdAndWorkspaceIdAndStatusNot(UUID tenantId, UUID workspaceId, String status);
}

interface EquipmentAlertEventRepository extends JpaRepository<EquipmentAlertEventEntity, UUID> {
	List<EquipmentAlertEventEntity> findByAlertIdOrderByOccurredAtDesc(UUID alertId);
	Optional<EquipmentAlertEventEntity> findByAlertIdAndRequestId(UUID alertId, String requestId);
}
