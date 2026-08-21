package com.guanseq.production.api;

import java.util.List;
import java.util.UUID;

import com.guanseq.warehouse.api.WarehouseReferenceProvider.WarehouseOption;

public record MaterialIssueReferenceData(
		List<ProductionOrderOption> productionOrders,
		List<WarehouseOption> warehouses,
		List<LocationOption> locations) {

	public record ProductionOrderOption(
			UUID id,
			String orderNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			java.math.BigDecimal plannedQuantity,
			java.time.LocalDate plannedStartDate,
			String workshop,
			String owner) { }

	public record LocationOption(UUID id, UUID warehouseId, String code, String name, String locationType) { }
}
