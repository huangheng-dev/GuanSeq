package com.guanseq.equipment.api;

import java.util.List;
import java.util.UUID;

public record EquipmentSparePartReferenceData(List<MaterialOption> materials, List<WarehouseOption> warehouses,
		List<LocationOption> locations) {

	public record MaterialOption(UUID id, String code, String name, String specification, String unit) { }
	public record WarehouseOption(UUID id, String code, String name) { }
	public record LocationOption(UUID id, UUID warehouseId, String code, String name, String locationType) { }
}
