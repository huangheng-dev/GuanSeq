package com.guanseq.warehouse.api;

import java.util.List;
import java.util.UUID;

public interface WarehouseReferenceProvider {

	List<WarehouseOption> listActiveWarehouses(UUID tenantOrganizationId);

	List<LocationOption> listActiveLocations(UUID tenantOrganizationId);

	record WarehouseOption(UUID id, String code, String name) { }

	record LocationOption(UUID id, UUID warehouseId, String code, String name, String locationType) { }
}
