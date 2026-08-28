package com.guanseq.sales.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SalesReturnReferenceData(List<ReturnableOrder> orders, List<WarehouseOption> warehouses,
		List<LocationOption> locations, boolean canCreate) {

	public record ReturnableOrder(UUID id, String orderNumber, UUID customerId, String customerCode,
			String customerName, String status, long version, List<ReturnableLine> lines) { }

	public record ReturnableLine(UUID id, int lineNumber, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, BigDecimal grossDeliveredQuantity, BigDecimal returnedQuantity,
			BigDecimal pendingReturnQuantity, BigDecimal netDeliveredQuantity, BigDecimal returnableQuantity) { }

	public record WarehouseOption(UUID id, String code, String name) { }

	public record LocationOption(UUID id, UUID warehouseId, String code, String name, String locationType) { }
}
