package com.guanseq.product.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BomReferenceProvider {

	boolean hasEffectiveBom(UUID tenantOrganizationId, UUID parentMaterialId, LocalDate effectiveDate);

	Optional<EffectiveBom> findEffectiveBom(UUID tenantOrganizationId, UUID parentMaterialId, LocalDate effectiveDate);

	record EffectiveBom(UUID id, UUID parentMaterialId, BigDecimal baseQuantity, List<Component> components) { }

	record Component(UUID materialId, String materialCode, String materialName, String unit,
			BigDecimal quantity, BigDecimal scrapRate) { }
}
