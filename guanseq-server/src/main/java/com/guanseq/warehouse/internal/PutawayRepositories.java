package com.guanseq.warehouse.internal;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PutawayTaskRepository extends JpaRepository<PutawayTaskEntity, UUID> {
	@Query("""
		select t from PutawayTaskEntity t where t.tenantOrganizationId=:tenant and t.workspaceId=:workspace
		and (:status='' or t.status=:status)
		and (:query='' or lower(t.taskNumber) like lower(concat('%',:query,'%'))
		 or lower(t.materialCode) like lower(concat('%',:query,'%'))
		 or lower(t.materialName) like lower(concat('%',:query,'%'))
		 or lower(t.sourceLocationCode) like lower(concat('%',:query,'%'))
		 or lower(t.targetLocationCode) like lower(concat('%',:query,'%')))
		""")
	Page<PutawayTaskEntity> search(@Param("tenant") UUID tenant, @Param("workspace") UUID workspace,
			@Param("query") String query, @Param("status") String status, Pageable pageable);
	Optional<PutawayTaskEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenant, UUID workspace);
	Optional<PutawayTaskEntity> findByTenantOrganizationIdAndCreateRequestId(UUID tenant, String requestId);
	@Query("select coalesce(sum(t.quantity),0) from PutawayTaskEntity t where t.tenantOrganizationId=:tenant and t.sourceBalanceId=:balance and t.status='OPEN'")
	BigDecimal openQuantity(@Param("tenant") UUID tenant, @Param("balance") UUID balance);
}

interface PutawayEventRepository extends JpaRepository<PutawayEventEntity, UUID> {
	Optional<PutawayEventEntity> findByTenantOrganizationIdAndRequestId(UUID tenant, String requestId);
}

