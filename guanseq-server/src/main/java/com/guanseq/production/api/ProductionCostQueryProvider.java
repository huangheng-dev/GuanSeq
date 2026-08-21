package com.guanseq.production.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductionCostQueryProvider {

	List<ProductionCostData> findCostsForSalesOrder(UUID tenantOrganizationId, UUID salesOrderId);

	record ProductionCostData(
			UUID productionOrderId,
			String productionOrderNumber,
			UUID salesOrderId,
			UUID materialId,
			BigDecimal acceptedQuantity,
			List<ConsumedMaterial> materials,
			List<CompletedOperation> operations) { }

	record ConsumedMaterial(
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal netQuantity) { }

	record CompletedOperation(
			UUID taskId,
			String taskNumber,
			String operationCode,
			String operationName,
			String workCenterCode,
			String workCenterName,
			BigDecimal setupMinutes,
			BigDecimal runMinutesPerUnit,
			BigDecimal completedQuantity,
			Instant completedAt,
			BigDecimal approvedActualLaborMinutes) { }
}
