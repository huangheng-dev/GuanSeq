package com.guanseq.warehouse.internal;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TransferTaskRepository extends JpaRepository<TransferTaskEntity,UUID> {
    @Query("""
        select t from TransferTaskEntity t where t.tenantOrganizationId=:tenant and t.workspaceId=:workspace
        and (:status='' or t.status=:status)
        and (:query='' or lower(t.taskNumber) like lower(concat('%',:query,'%'))
          or lower(t.materialCode) like lower(concat('%',:query,'%'))
          or lower(t.materialName) like lower(concat('%',:query,'%'))
          or lower(t.sourceLocationCode) like lower(concat('%',:query,'%'))
          or lower(t.targetLocationCode) like lower(concat('%',:query,'%')))
        """)
    Page<TransferTaskEntity> search(@Param("tenant") UUID tenant,@Param("workspace") UUID workspace,
            @Param("query") String query,@Param("status") String status,Pageable pageable);
    Optional<TransferTaskEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id,UUID tenant,UUID workspace);
    Optional<TransferTaskEntity> findByTenantOrganizationIdAndCreateRequestId(UUID tenant,String requestId);
    @Query("select coalesce(sum(t.quantity),0) from TransferTaskEntity t where t.tenantOrganizationId=:tenant and t.sourceBalanceId=:balance and t.status='OPEN'")
    BigDecimal openQuantity(@Param("tenant") UUID tenant,@Param("balance") UUID balance);
    @Query("select (count(t)>0) from TransferTaskEntity t where t.tenantOrganizationId=:tenant and t.sourceBalanceId=:balance and t.status='OPEN'")
    boolean hasOpenSource(@Param("tenant") UUID tenant,@Param("balance") UUID balance);
}

interface TransferEventRepository extends JpaRepository<TransferEventEntity,UUID> {
    Optional<TransferEventEntity> findByTenantOrganizationIdAndRequestId(UUID tenant,String requestId);
}

interface StockCountTaskRepository extends JpaRepository<StockCountTaskEntity,UUID> {
    @Query("""
        select t from StockCountTaskEntity t where t.tenantOrganizationId=:tenant and t.workspaceId=:workspace
        and (:status='' or t.status=:status)
        and (:query='' or lower(t.countNumber) like lower(concat('%',:query,'%'))
          or lower(t.materialCode) like lower(concat('%',:query,'%'))
          or lower(t.materialName) like lower(concat('%',:query,'%'))
          or lower(t.locationCode) like lower(concat('%',:query,'%'))
          or lower(t.lotNumber) like lower(concat('%',:query,'%')))
        """)
    Page<StockCountTaskEntity> search(@Param("tenant") UUID tenant,@Param("workspace") UUID workspace,
            @Param("query") String query,@Param("status") String status,Pageable pageable);
    Optional<StockCountTaskEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id,UUID tenant,UUID workspace);
    Optional<StockCountTaskEntity> findByTenantOrganizationIdAndCreateRequestId(UUID tenant,String requestId);
    @Query("select (count(t)>0) from StockCountTaskEntity t where t.tenantOrganizationId=:tenant and t.balanceId=:balance and t.status in ('OPEN','COUNTED')")
    boolean hasActiveBalance(@Param("tenant") UUID tenant,@Param("balance") UUID balance);
}

interface StockCountEventRepository extends JpaRepository<StockCountEventEntity,UUID> {
    Optional<StockCountEventEntity> findByTenantOrganizationIdAndRequestId(UUID tenant,String requestId);
}
