package com.guanseq.production.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OperationTaskRepository extends JpaRepository<OperationTaskEntity, UUID> {
	@Query("""
			select t from OperationTaskEntity t where t.tenantOrganizationId = :tenantId
			and (:status = '' or t.status = :status)
			and (:query = ''
				or lower(t.taskNumber) like lower(concat('%', :query, '%'))
				or lower(t.orderNumber) like lower(concat('%', :query, '%'))
				or lower(t.materialCode) like lower(concat('%', :query, '%'))
				or lower(t.materialName) like lower(concat('%', :query, '%'))
				or lower(t.operationCode) like lower(concat('%', :query, '%'))
				or lower(t.operationName) like lower(concat('%', :query, '%'))
				or lower(t.workCenterCode) like lower(concat('%', :query, '%')))
			""")
	Page<OperationTaskEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<OperationTaskEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	Page<OperationTaskEntity> findByTenantOrganizationId(UUID tenantId, Pageable pageable);
	List<OperationTaskEntity> findByTenantOrganizationIdAndOrderIdOrderBySequenceNumberAsc(UUID tenantId, UUID orderId);
	Optional<OperationTaskEntity> findFirstByTenantOrganizationIdAndOrderIdOrderBySequenceNumberDesc(UUID tenantId, UUID orderId);
	boolean existsByTenantOrganizationIdAndOrderId(UUID tenantId, UUID orderId);
	boolean existsByTenantOrganizationIdAndOrderIdAndStatusNot(UUID tenantId, UUID orderId, String status);
}

interface OperationEventRepository extends JpaRepository<OperationEventEntity, UUID> {
	List<OperationEventEntity> findByTaskIdOrderByOccurredAtDesc(UUID taskId);
	Optional<OperationEventEntity> findFirstByTenantOrganizationIdAndRequestIdAndActionIn(UUID tenantId, String requestId, List<String> actions);
}
