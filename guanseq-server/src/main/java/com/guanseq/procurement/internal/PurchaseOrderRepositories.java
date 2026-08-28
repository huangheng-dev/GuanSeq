package com.guanseq.procurement.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.repository.query.Param;

interface SupplierRepository extends JpaRepository<SupplierEntity, UUID> {
	@Query("""
		select s from SupplierEntity s where s.tenantOrganizationId = :tenantId
		and (:status = 'ALL' or s.status = :status)
		and (:query = '' or lower(s.code) like lower(concat('%', :query, '%'))
			or lower(s.name) like lower(concat('%', :query, '%'))
			or lower(s.contactName) like lower(concat('%', :query, '%')))
		order by s.code asc
		""")
	Page<SupplierEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);
	List<SupplierEntity> findByTenantOrganizationIdAndStatusOrderByCodeAsc(UUID tenantId, String status);
	Optional<SupplierEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	Optional<SupplierEntity> findByIdAndTenantOrganizationIdAndStatus(UUID id, UUID tenantId, String status);
	boolean existsByTenantOrganizationIdAndCode(UUID tenantId, String code);
}

interface SupplierEventRepository extends JpaRepository<SupplierEventEntity, UUID> { }

interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, UUID> {
	@Query("""
		select distinct o from PurchaseOrderEntity o join fetch o.lines l
		where o.tenantOrganizationId = :tenantId and l.receivedQuantity - l.returnedQuantity > 0
		order by o.orderNumber asc
		""")
	List<PurchaseOrderEntity> findReceivedOrders(@Param("tenantId") UUID tenantId);

	@Query("""
		select distinct o from PurchaseOrderEntity o join fetch o.lines l
		where o.id = :id and o.tenantOrganizationId = :tenantId
		and exists (select 1 from PurchaseOrderLineEntity receivedLine
			where receivedLine.order = o and receivedLine.receivedQuantity - receivedLine.returnedQuantity > 0)
		""")
	Optional<PurchaseOrderEntity> findReceivedOrder(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

	@Query("""
		select o from PurchaseOrderEntity o where o.tenantOrganizationId = :tenantId
		and (:status = '' or o.status = :status)
		and (:query = '' or lower(o.orderNumber) like lower(concat('%', :query, '%'))
			or lower(o.supplierCode) like lower(concat('%', :query, '%'))
			or lower(o.supplierName) like lower(concat('%', :query, '%')))
		""")
	Page<PurchaseOrderEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<PurchaseOrderEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
	List<PurchaseOrderEntity> findByTenantOrganizationIdAndStatus(UUID tenantId, String status);
	Optional<PurchaseOrderEntity> findByTenantOrganizationIdAndSourceTypeAndSourceId(UUID tenantId, String sourceType, UUID sourceId);

	@Query("""
		select distinct o from PurchaseOrderEntity o join fetch o.lines l
		where o.tenantOrganizationId = :tenantId and o.status = 'RELEASED'
		and o.promisedReceiptDate <= :horizonEnd and l.materialId in :materialIds
		and l.receivedQuantity - l.returnedQuantity < l.orderedQuantity
		""")
	List<PurchaseOrderEntity> findReleasedReceipts(@Param("tenantId") UUID tenantId,
			@Param("materialIds") Collection<UUID> materialIds, @Param("horizonEnd") LocalDate horizonEnd);
}

interface PurchaseOrderEventRepository extends JpaRepository<PurchaseOrderEventEntity, UUID> { }


@Entity
@Table(schema = "procurement", name = "supplier_events")
class SupplierEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "supplier_id") private UUID supplierId;
	private String action;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> details = Map.of();
	@Column(name = "occurred_at") private Instant occurredAt;

	protected SupplierEventEntity() { }
	SupplierEventEntity(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID supplierId,
			String action, String requestId, Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantOrganizationId;
		this.workspaceId = workspaceId;
		this.actorUserId = actorUserId;
		this.supplierId = supplierId;
		this.action = action;
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}
}
