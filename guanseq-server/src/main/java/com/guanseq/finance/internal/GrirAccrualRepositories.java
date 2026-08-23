package com.guanseq.finance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GrirAccrualRepository extends JpaRepository<GrirAccrualEntity, UUID> {

	@Query("""
			select a from GrirAccrualEntity a
			where a.tenantOrganizationId = :tenantId
			  and (:year is null or a.fiscalYear = :year)
			  and (:status = '' or a.status = :status)
			""")
	Page<GrirAccrualEntity> search(@Param("tenantId") UUID tenantId, @Param("year") Integer year,
			@Param("status") String status, Pageable pageable);

	Optional<GrirAccrualEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);

	Optional<GrirAccrualEntity> findByTenantOrganizationIdAndRequestId(UUID tenantOrganizationId, String requestId);

	Optional<GrirAccrualEntity> findByTenantOrganizationIdAndFiscalYearAndFiscalPeriod(
			UUID tenantOrganizationId, int fiscalYear, int fiscalPeriod);

	@Query("""
			select a from GrirAccrualEntity a
			where a.tenantOrganizationId = :tenantId
			  and a.status = 'POSTED'
			  and (a.fiscalYear < :year or (a.fiscalYear = :year and a.fiscalPeriod < :period))
			order by a.fiscalYear desc, a.fiscalPeriod desc
			""")
	List<GrirAccrualEntity> findPriorPosted(@Param("tenantId") UUID tenantId,
			@Param("year") int year, @Param("period") int period);
}

interface GrirAccrualEventRepository extends JpaRepository<GrirAccrualEventEntity, UUID> { }
