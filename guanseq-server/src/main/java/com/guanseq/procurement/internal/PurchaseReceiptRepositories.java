package com.guanseq.procurement.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceiptEntity, UUID> {
	@Query("""
		select distinct r from PurchaseReceiptEntity r left join fetch r.lines
		where r.tenantOrganizationId = :tenantId
		and (:status = '' or r.status = :status)
		and (:query = '' or lower(r.receiptNumber) like lower(concat('%', :query, '%'))
			or lower(r.orderNumber) like lower(concat('%', :query, '%'))
			or lower(r.supplierCode) like lower(concat('%', :query, '%'))
				or lower(r.supplierName) like lower(concat('%', :query, '%')))
		""")
	Page<PurchaseReceiptEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	@Query("select r from PurchaseReceiptEntity r left join fetch r.lines where r.id = :id and r.tenantOrganizationId = :tenantId")
	Optional<PurchaseReceiptEntity> findByIdAndTenantOrganizationId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
	Optional<PurchaseReceiptEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
}

interface PurchaseReceiptLineRepository extends JpaRepository<PurchaseReceiptLineEntity, UUID> {
	@Query("select l from PurchaseReceiptLineEntity l join fetch l.receipt r where l.inspectionId = :inspectionId and l.tenantOrganizationId = :tenantId")
	Optional<PurchaseReceiptLineEntity> findByTenantOrganizationIdAndInspectionId(@Param("tenantId") UUID tenantId, @Param("inspectionId") UUID inspectionId);
}

interface PurchaseReceiptEventRepository extends JpaRepository<PurchaseReceiptEventEntity, UUID> { }
