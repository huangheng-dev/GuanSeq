package com.guanseq.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesShipmentReferenceData(List<ReleasedOrder> releasedOrders, List<WarehouseOption> warehouses) {

	public record ReleasedOrder(
			UUID id,
			String orderNumber,
			UUID customerId,
			String customerCode,
			String customerName,
			LocalDate promisedDeliveryDate,
			List<ReleasedLine> lines) {
	}

	public record ReleasedLine(
			UUID id,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal orderedQuantity,
			BigDecimal grossDeliveredQuantity,
			BigDecimal returnedQuantity,
			BigDecimal deliveredQuantity,
			BigDecimal outstandingQuantity) {
	}

	public record WarehouseOption(UUID id, String code, String name) {
	}
}
