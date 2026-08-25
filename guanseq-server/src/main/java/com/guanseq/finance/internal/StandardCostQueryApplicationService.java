package com.guanseq.finance.internal;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guanseq.finance.api.StandardCostProvider;

@Service
class StandardCostQueryApplicationService implements StandardCostProvider {

	private final ItemStandardCostRepository repository;

	StandardCostQueryApplicationService(ItemStandardCostRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<StandardCost> findEffectiveCost(UUID tenantOrganizationId, UUID materialId, LocalDate effectiveDate) {
		return repository
				.findFirstByTenantOrganizationIdAndMaterialIdAndStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
						tenantOrganizationId, materialId, "ACTIVE", effectiveDate)
				.map(item -> new StandardCost(item.getMaterialId(), item.getUnitCost(), item.getCurrency(), item.getEffectiveDate()));
	}
}
