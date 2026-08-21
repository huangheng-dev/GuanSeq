package com.guanseq.planning.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MrpRunRepository extends JpaRepository<MrpRunEntity, UUID> {

	@Query("""
			select r from MrpRunEntity r
			where r.tenantOrganizationId = :tenantId
			and (:status = '' or r.status = :status)
			and (:query = '' or lower(r.runNumber) like lower(concat('%', :query, '%'))
				or lower(r.name) like lower(concat('%', :query, '%')))
			""")
	Page<MrpRunEntity> search(@Param("tenantId") UUID tenantId, @Param("query") String query,
			@Param("status") String status, Pageable pageable);

	Optional<MrpRunEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);

	Optional<MrpRunEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
}

interface MrpRunDemandRepository extends JpaRepository<MrpRunDemandEntity, UUID> {
	List<MrpRunDemandEntity> findByRunIdOrderByRequiredDateAscDemandNumberAsc(UUID runId);
}

interface MrpRunSupplyRepository extends JpaRepository<MrpRunSupplyEntity, UUID> {
	List<MrpRunSupplyEntity> findByRunIdOrderByMaterialCodeAsc(UUID runId);
}

interface MrpRunScheduledReceiptRepository extends JpaRepository<MrpRunScheduledReceiptEntity, UUID> {
	List<MrpRunScheduledReceiptEntity> findByRunIdOrderByExpectedReceiptDateAscSourceOrderNumberAsc(UUID runId);
}

interface MrpRunNetRequirementRepository extends JpaRepository<MrpRunNetRequirementEntity, UUID> {
	List<MrpRunNetRequirementEntity> findByRunIdOrderByRequiredDateAscRequirementLevelAscMaterialCodeAsc(UUID runId);

	@Query("""
		select n from MrpRunNetRequirementEntity n
		where n.tenantOrganizationId = :tenantId
		and n.recommendationType in ('PRODUCTION', 'PURCHASE', 'OUTSOURCE')
		and (:status = '' or n.decisionStatus = :status)
		and (:type = '' or n.recommendationType = :type)
		and (:query = '' or lower(n.materialCode) like lower(concat('%', :query, '%'))
			or lower(n.materialName) like lower(concat('%', :query, '%'))
			or exists (select r.id from MrpRunEntity r where r.id = n.runId
				and (lower(r.runNumber) like lower(concat('%', :query, '%'))
					or lower(r.name) like lower(concat('%', :query, '%')))))
		""")
	Page<MrpRunNetRequirementEntity> searchSuggestions(@Param("tenantId") UUID tenantId,
			@Param("query") String query, @Param("status") String status, @Param("type") String type,
			Pageable pageable);

	Optional<MrpRunNetRequirementEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantId);
}

interface MrpSuggestionEventRepository extends JpaRepository<MrpSuggestionEventEntity, UUID> {
	Optional<MrpSuggestionEventEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
}

interface MrpRunExceptionRepository extends JpaRepository<MrpRunExceptionEntity, UUID> {
	List<MrpRunExceptionEntity> findByRunIdOrderByCodeAscMaterialCodeAsc(UUID runId);
}

interface MrpRunEventRepository extends JpaRepository<MrpRunEventEntity, UUID> {
}
