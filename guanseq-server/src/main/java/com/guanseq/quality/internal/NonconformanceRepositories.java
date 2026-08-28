package com.guanseq.quality.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NonconformanceRepository extends JpaRepository<NonconformanceEntity, UUID> {
	@Query("""
		select n from NonconformanceEntity n
		where n.tenantOrganizationId = :tenantId and n.workspaceId = :workspaceId
		and (:queue = 'ALL'
			or (:queue = 'REVIEW' and n.status in ('OPEN', 'REVIEWED'))
			or (:queue = 'ACTION' and n.status in ('ACTION_REQUIRED', 'ACTION_IN_PROGRESS', 'VERIFICATION_PENDING')))
		and (:status = '' or n.status = :status)
		and (:severity = '' or n.severity = :severity)
		and (:sourceType = '' or n.sourceType = :sourceType)
		and (:overdue = false or (n.actionDueDate < current_date and n.status in ('ACTION_REQUIRED', 'ACTION_IN_PROGRESS', 'VERIFICATION_PENDING')))
		and (:query = '' or lower(n.caseNumber) like lower(concat('%', :query, '%'))
			or lower(n.inspectionNumber) like lower(concat('%', :query, '%'))
			or lower(n.sourceDocumentNumber) like lower(concat('%', :query, '%'))
			or lower(n.orderNumber) like lower(concat('%', :query, '%'))
			or lower(n.materialCode) like lower(concat('%', :query, '%'))
			or lower(n.materialName) like lower(concat('%', :query, '%'))
			or lower(coalesce(n.supplierName, '')) like lower(concat('%', :query, '%')))
		""")
	Page<NonconformanceEntity> search(
			@Param("tenantId") UUID tenantId,
			@Param("workspaceId") UUID workspaceId,
			@Param("query") String query,
			@Param("queue") String queue,
			@Param("status") String status,
			@Param("severity") String severity,
			@Param("sourceType") String sourceType,
			@Param("overdue") boolean overdue,
			Pageable pageable);

	Optional<NonconformanceEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId, UUID workspaceId);
	Optional<NonconformanceEntity> findByTenantOrganizationIdAndInspectionId(UUID tenantId, UUID inspectionId);
	Optional<NonconformanceEntity> findByTenantOrganizationIdAndWorkspaceIdAndCreateRequestId(UUID tenantId, UUID workspaceId, String requestId);

	@Query("""
		select n.status, count(n) from NonconformanceEntity n
		where n.tenantOrganizationId = :tenantId and n.workspaceId = :workspaceId
		group by n.status
		""")
	List<Object[]> statusCounts(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId);

	@Query("""
		select count(n) from NonconformanceEntity n
		where n.tenantOrganizationId = :tenantId and n.workspaceId = :workspaceId
		and n.actionDueDate < current_date
		and n.status in ('ACTION_REQUIRED', 'ACTION_IN_PROGRESS', 'VERIFICATION_PENDING')
		""")
	long overdueCount(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId);
}

interface NonconformanceEventRepository extends JpaRepository<NonconformanceEventEntity, UUID> {
	List<NonconformanceEventEntity> findByNonconformanceIdOrderByOccurredAtAscIdAsc(UUID nonconformanceId);
	Optional<NonconformanceEventEntity> findByTenantOrganizationIdAndWorkspaceIdAndRequestId(UUID tenantId, UUID workspaceId, String requestId);
}
