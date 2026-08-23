package com.guanseq.finance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountingPeriodRepository extends JpaRepository<AccountingPeriodEntity, UUID> {

	List<AccountingPeriodEntity> findByTenantOrganizationIdAndFiscalYearOrderByFiscalPeriodAsc(
			UUID tenantOrganizationId, int fiscalYear);

	Optional<AccountingPeriodEntity> findByTenantOrganizationIdAndFiscalYearAndFiscalPeriod(
			UUID tenantOrganizationId, int fiscalYear, int fiscalPeriod);

	Optional<AccountingPeriodEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
}
