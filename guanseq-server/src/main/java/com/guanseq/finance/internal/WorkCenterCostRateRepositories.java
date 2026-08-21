package com.guanseq.finance.internal;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkCenterCostRateRepository extends JpaRepository<WorkCenterCostRateEntity, UUID> {
	@Query("""
		select r from WorkCenterCostRateEntity r
		where r.tenantOrganizationId = :tenantId and r.owningOrganizationId = :organizationId
		and (:status = '' or r.status = :status)
		and (:query = '' or lower(r.workCenterCode) like lower(concat('%', :query, '%'))
			or lower(r.workCenterName) like lower(concat('%', :query, '%')))
		""")
	Page<WorkCenterCostRateEntity> search(@Param("tenantId") UUID tenantId, @Param("organizationId") UUID organizationId,
			@Param("query") String query, @Param("status") String status, Pageable pageable);

	Optional<WorkCenterCostRateEntity> findByIdAndTenantOrganizationIdAndOwningOrganizationId(
			UUID id, UUID tenantOrganizationId, UUID owningOrganizationId);
	Optional<WorkCenterCostRateEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
	Optional<WorkCenterCostRateEntity> findByTenantOrganizationIdAndOwningOrganizationIdAndWorkCenterCodeAndEffectiveDate(
			UUID tenantOrganizationId, UUID owningOrganizationId, String workCenterCode, LocalDate effectiveDate);
	Optional<WorkCenterCostRateEntity> findFirstByTenantOrganizationIdAndOwningOrganizationIdAndWorkCenterCodeAndStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
			UUID tenantOrganizationId, UUID owningOrganizationId, String workCenterCode, String status, LocalDate effectiveDate);
}

interface WorkCenterCostRateEventRepository extends JpaRepository<WorkCenterCostRateEventEntity, UUID> {
	Optional<WorkCenterCostRateEventEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);
}
