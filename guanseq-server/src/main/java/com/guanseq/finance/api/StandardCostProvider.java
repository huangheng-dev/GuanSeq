package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface StandardCostProvider {

	Optional<StandardCost> findEffectiveCost(UUID tenantOrganizationId, UUID materialId, LocalDate effectiveDate);

	record StandardCost(UUID materialId, BigDecimal unitCost, String currency, LocalDate effectiveDate) { }
}
