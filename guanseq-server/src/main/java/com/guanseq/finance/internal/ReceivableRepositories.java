package com.guanseq.finance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReceivableInvoiceRepository extends JpaRepository<ReceivableInvoiceEntity, UUID> {

	@Query("""
			select i from ReceivableInvoiceEntity i
			where i.tenantOrganizationId = :tenantId
			  and (:status = '' or i.status = :status)
			  and (:query = '' or lower(i.invoiceNumber) like lower(concat('%', :query, '%'))
			       or lower(i.orderNumber) like lower(concat('%', :query, '%'))
			       or lower(i.customerCode) like lower(concat('%', :query, '%'))
			       or lower(i.customerName) like lower(concat('%', :query, '%')))
			""")
	Page<ReceivableInvoiceEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<ReceivableInvoiceEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
	Optional<ReceivableInvoiceEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
	List<ReceivableInvoiceEntity> findByTenantOrganizationId(UUID tenantOrganizationId);
	List<ReceivableInvoiceEntity> findByTenantOrganizationIdAndSalesOrderId(UUID tenantOrganizationId, UUID salesOrderId);
}

interface ReceivableReceiptRepository extends JpaRepository<ReceivableReceiptEntity, UUID> {
	Optional<ReceivableReceiptEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
	Optional<ReceivableReceiptEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
}

interface ReceivableCreditNoteRepository extends JpaRepository<ReceivableCreditNoteEntity, UUID> {
	@Query("""
			select cn from ReceivableCreditNoteEntity cn
			where cn.tenantOrganizationId = :tenantId
			  and (:query = '' or lower(cn.creditNoteNumber) like lower(concat('%', :query, '%'))
			       or lower(cn.originalInvoiceNumber) like lower(concat('%', :query, '%'))
			       or lower(cn.customerCode) like lower(concat('%', :query, '%'))
			       or lower(cn.customerName) like lower(concat('%', :query, '%')))
			""")
	Page<ReceivableCreditNoteEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query, Pageable pageable);

	Optional<ReceivableCreditNoteEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
	Optional<ReceivableCreditNoteEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
	List<ReceivableCreditNoteEntity> findByTenantOrganizationIdAndOriginalInvoiceId(UUID tenantOrganizationId, UUID originalInvoiceId);
}

interface ReceivableReversalRepository extends JpaRepository<ReceivableReversalEntity, UUID> {
	Optional<ReceivableReversalEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
}

interface ReceivableEventRepository extends JpaRepository<ReceivableEventEntity, UUID> { }
