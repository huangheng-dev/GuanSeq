package com.guanseq.production.api;

import java.util.List;
import java.util.UUID;

import com.guanseq.warehouse.api.WarehouseReferenceProvider.WarehouseOption;

public record MaterialIssueReferenceData(
		boolean canControl,
		List<ProductionOrderOption> productionOrders,
		List<WarehouseOption> warehouses,
		List<LocationOption> locations,
		List<AvailableStockOption> availableStocks) {

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

	public record AvailableStockOption(
			UUID id,
			UUID warehouseId,
			String warehouseCode,
			UUID locationId,
			String locationCode,
			String locationName,
			UUID materialId,
			String materialCode,
			String lotNumber,
			java.math.BigDecimal availableQuantity,
			long version) { }
}
