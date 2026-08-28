package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WarehousePutawayReferenceData(List<SourceBalance> sourceBalances, List<TargetLocation> targetLocations) {
	public record SourceBalance(UUID id, long version, UUID warehouseId, String warehouseCode, String warehouseName,
			UUID locationId, String locationCode, String locationName, String materialCode, String materialName,
			String materialSpecification, String lotNumber, String unit, BigDecimal availableQuantity, BigDecimal reservedOpenQuantity) { }
	public record TargetLocation(UUID id, UUID warehouseId, String warehouseCode, String code, String name, String scanCode) { }
}

