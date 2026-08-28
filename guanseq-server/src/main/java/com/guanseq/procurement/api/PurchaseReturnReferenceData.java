package com.guanseq.procurement.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchaseReturnReferenceData(List<ReturnableOrder> orders, boolean canCreate) {
	public record ReturnableOrder(UUID id, String orderNumber, UUID supplierId, String supplierCode,
			String supplierName, long version, List<ReturnableLine> lines) { }
	public record ReturnableLine(UUID purchaseReceiptLineId, String receiptNumber, UUID purchaseOrderLineId,
			UUID materialId, String materialCode, String materialName, String materialSpecification, String unit,
			String qualityStatus, UUID stockBalanceId, String warehouseCode, String locationCode, String lotNumber,
			BigDecimal sourceQuantity, BigDecimal pendingQuantity, BigDecimal stockAvailableQuantity,
			BigDecimal returnableQuantity) { }
}
