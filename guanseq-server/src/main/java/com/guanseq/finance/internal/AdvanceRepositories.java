package com.guanseq.finance.internal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AdvanceRepository extends JpaRepository<AdvanceEntity, UUID> {

	@Query("""
			select a from AdvanceEntity a
			where a.tenantOrganizationId = :tenantId
			  and (:type = '' or a.type = :type)
			  and (:status = '' or a.status = :status)
			  and (:partyId is null or a.partyId = :partyId)
			  and (:query = '' or lower(a.advanceNumber) like lower(concat('%', :query, '%'))
			       or lower(a.partyCode) like lower(concat('%', :query, '%'))
			       or lower(a.partyName) like lower(concat('%', :query, '%')))
			""")
	Page<AdvanceEntity> search(@Param("tenantId") UUID tenantId,
			@Param("type") String type,
			@Param("status") String status,
			@Param("partyId") UUID partyId,
			@Param("query") String query,
			Pageable pageable);

	java.util.Optional<AdvanceEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);

	java.util.Optional<AdvanceEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);

	@Query("""
			select a from AdvanceEntity a
			where a.tenantOrganizationId = :tenantId
			  and a.type = :type
			  and a.partyId = :partyId
			  and a.status <> 'CLOSED'
			order by a.advanceDate desc
			""")
	java.util.List<AdvanceEntity> findOpenByParty(@Param("tenantId") UUID tenantId,
			@Param("type") String type,
			@Param("partyId") UUID partyId);
}

interface AdvanceApplicationRepository extends JpaRepository<AdvanceApplicationEntity, UUID> {
	java.util.List<AdvanceApplicationEntity> findByAdvanceIdOrderByApplicationDateDesc(UUID advanceId);
}

interface AdvanceRefundRepository extends JpaRepository<AdvanceRefundEntity, UUID> {
	java.util.List<AdvanceRefundEntity> findByAdvanceIdOrderByRefundDateDesc(UUID advanceId);
}

interface AdvanceEventRepository extends JpaRepository<AdvanceEventEntity, UUID> {
	java.util.List<AdvanceEventEntity> findByAdvanceIdOrderByOccurredAtDesc(UUID advanceId);
}
