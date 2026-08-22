package com.guanseq.finance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PayableInvoiceRepository extends JpaRepository<PayableInvoiceEntity, UUID> {

	@Query("""
			select i from PayableInvoiceEntity i
			where i.tenantOrganizationId = :tenantId
			  and (:status = '' or i.status = :status)
			  and (:query = '' or lower(i.invoiceNumber) like lower(concat('%', :query, '%'))
			       or lower(i.supplierInvoiceNumber) like lower(concat('%', :query, '%'))
			       or lower(i.orderNumber) like lower(concat('%', :query, '%'))
			       or lower(i.supplierCode) like lower(concat('%', :query, '%'))
			       or lower(i.supplierName) like lower(concat('%', :query, '%')))
			""")
	Page<PayableInvoiceEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<PayableInvoiceEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
	Optional<PayableInvoiceEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
	Optional<PayableInvoiceEntity> findByTenantOrganizationIdAndSupplierIdAndSupplierInvoiceNumber(
			UUID tenantOrganizationId, UUID supplierId, String supplierInvoiceNumber);
	List<PayableInvoiceEntity> findByTenantOrganizationId(UUID tenantOrganizationId);
	List<PayableInvoiceEntity> findByTenantOrganizationIdAndPurchaseOrderId(UUID tenantOrganizationId, UUID purchaseOrderId);
}

interface PayablePaymentRepository extends JpaRepository<PayablePaymentEntity, UUID> {
	Optional<PayablePaymentEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
	Optional<PayablePaymentEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
}

interface PayableCreditNoteRepository extends JpaRepository<PayableCreditNoteEntity, UUID> {
	@Query("""
			select cn from PayableCreditNoteEntity cn
			where cn.tenantOrganizationId = :tenantId
			  and (:query = '' or lower(cn.creditNoteNumber) like lower(concat('%', :query, '%'))
			       or lower(cn.originalInvoiceNumber) like lower(concat('%', :query, '%'))
			       or lower(cn.supplierCode) like lower(concat('%', :query, '%'))
			       or lower(cn.supplierName) like lower(concat('%', :query, '%')))
			""")
	Page<PayableCreditNoteEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query, Pageable pageable);

	Optional<PayableCreditNoteEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
	Optional<PayableCreditNoteEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
	List<PayableCreditNoteEntity> findByTenantOrganizationIdAndOriginalInvoiceId(UUID tenantOrganizationId, UUID originalInvoiceId);
}

interface PayableReversalRepository extends JpaRepository<PayableReversalEntity, UUID> {
	Optional<PayableReversalEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
}

interface PayableEventRepository extends JpaRepository<PayableEventEntity, UUID> { }
