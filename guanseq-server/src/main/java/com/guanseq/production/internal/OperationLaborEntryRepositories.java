package com.guanseq.production.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OperationLaborEntryRepository extends JpaRepository<OperationLaborEntryEntity, UUID> {
	@Query("""
			select e from OperationLaborEntryEntity e where e.tenantOrganizationId = :tenantId
			and (:status = '' or e.status = :status)
			and (:taskId is null or e.taskId = :taskId)
			and (:query = ''
				or lower(e.entryNumber) like lower(concat('%', :query, '%'))
				or lower(e.taskNumber) like lower(concat('%', :query, '%'))
				or lower(e.orderNumber) like lower(concat('%', :query, '%'))
				or lower(e.operationCode) like lower(concat('%', :query, '%'))
				or lower(e.operationName) like lower(concat('%', :query, '%'))
				or lower(e.workCenterCode) like lower(concat('%', :query, '%'))
				or lower(e.operatorName) like lower(concat('%', :query, '%')))
			""")
	Page<OperationLaborEntryEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, @Param("taskId") UUID taskId, Pageable pageable);

	Optional<OperationLaborEntryEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	Optional<OperationLaborEntryEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
	Optional<OperationLaborEntryEntity> findByTenantOrganizationIdAndApproveRequestId(UUID tenantId, String requestId);
	Optional<OperationLaborEntryEntity> findByTenantOrganizationIdAndVoidRequestId(UUID tenantId, String requestId);
	List<OperationLaborEntryEntity> findByTenantOrganizationIdAndTaskIdOrderByCreatedAtDesc(UUID tenantId, UUID taskId);

	@Query("select sum(e.actualMinutes) from OperationLaborEntryEntity e where e.tenantOrganizationId = :tenantId and e.taskId = :taskId and e.status = 'APPROVED'")
	BigDecimal sumApprovedMinutes(@Param("tenantId") UUID tenantId, @Param("taskId") UUID taskId);
}

interface OperationLaborEventRepository extends JpaRepository<OperationLaborEventEntity, UUID> {
	List<OperationLaborEventEntity> findByEntryIdOrderByOccurredAtDesc(UUID entryId);
	Optional<OperationLaborEventEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
}
