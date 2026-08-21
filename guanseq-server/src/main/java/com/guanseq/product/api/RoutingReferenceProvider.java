package com.guanseq.product.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RoutingReferenceProvider {

	boolean hasEffectiveRouting(UUID tenantOrganizationId, UUID materialId, LocalDate effectiveDate);

	EffectiveRouting findEffectiveRouting(UUID tenantOrganizationId, UUID materialId, LocalDate effectiveDate);

	record EffectiveRouting(
			UUID routingId,
			String routingNumber,
			String versionCode,
			UUID materialId,
			String materialCode,
			String materialName,
			BigDecimal baseQuantity,
			List<EffectiveOperation> operations) { }

	record EffectiveOperation(
			UUID operationId,
			int sequenceNumber,
			String operationCode,
			String operationName,
			String workCenterCode,
			String workCenterName,
			BigDecimal setupMinutes,
			BigDecimal runMinutesPerUnit,
			BigDecimal queueMinutes,
			boolean inspectionRequired,
			String instructionSummary) { }
}
