package com.guanseq.warehouse.api;

import java.util.List;
import java.util.UUID;

public record InventoryReferenceData(List<WarehouseOption> warehouses, List<LocationOption> locations) {
	public record WarehouseOption(UUID id, String code, String name) { }
	public record LocationOption(UUID id, UUID warehouseId, String code, String name, String locationType) { }
}
