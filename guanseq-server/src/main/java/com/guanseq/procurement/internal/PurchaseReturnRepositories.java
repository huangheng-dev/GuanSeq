package com.guanseq.procurement.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PurchaseReturnRepository extends JpaRepository<PurchaseReturnEntity,UUID>{
	@Query("""
		select r from PurchaseReturnEntity r where r.tenantOrganizationId=:tenantId
		and (:status='' or r.status=:status) and (:query='' or lower(r.returnNumber) like lower(concat('%',:query,'%'))
		or lower(r.orderNumber) like lower(concat('%',:query,'%')) or lower(r.supplierName) like lower(concat('%',:query,'%')))
		""")
	Page<PurchaseReturnEntity> search(@Param("tenantId") UUID tenantId,@Param("query") String query,@Param("status") String status,Pageable pageable);
	Optional<PurchaseReturnEntity> findByIdAndTenantOrganizationId(UUID id,UUID tenantId);
	Optional<PurchaseReturnEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId,String requestId);
}
interface PurchaseReturnLineRepository extends JpaRepository<PurchaseReturnLineEntity,UUID>{
	@Query("""
		select coalesce(sum(l.authorizedQuantity),0) from PurchaseReturnLineEntity l join l.purchaseReturn r
		where r.tenantOrganizationId=:tenantId and l.purchaseReceiptLineId=:receiptLineId and l.qualityStatus=:quality
		and r.status in ('PENDING_SHIPMENT','SHIPPED')
		""")
	BigDecimal sumActive(@Param("tenantId") UUID tenantId,@Param("receiptLineId") UUID receiptLineId,@Param("quality") String quality);
}
interface PurchaseReturnEventRepository extends JpaRepository<PurchaseReturnEventEntity,UUID>{
	List<PurchaseReturnEventEntity> findByReturnIdOrderByOccurredAtDesc(UUID returnId);
	Optional<PurchaseReturnEventEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId,String requestId);
}
