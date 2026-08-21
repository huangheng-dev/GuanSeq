package com.guanseq.procurement.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseReceiptReferenceData(
		List<ReleasedOrder> releasedOrders,
		List<WarehouseOption> warehouses,
		List<LocationOption> locations) {

	public record ReleasedOrder(UUID id, String orderNumber, UUID supplierId, String supplierCode, String supplierName,
			LocalDate promisedReceiptDate, List<ReleasedLine> lines) { }
	public record ReleasedLine(UUID id, int lineNumber, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, BigDecimal orderedQuantity, BigDecimal receivedQuantity,
			BigDecimal outstandingQuantity, boolean inspectionRequired) { }
	public record WarehouseOption(UUID id, String code, String name) { }
	public record LocationOption(UUID id, UUID warehouseId, String code, String name, String locationType) { }
}
