package com.guanseq.procurement.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PurchaseReceiptRecord(
		UUID id, String receiptNumber, UUID purchaseOrderId, String orderNumber, UUID supplierId,
		String supplierCode, String supplierName, UUID warehouseId, String warehouseCode, String warehouseName,
		UUID locationId, String locationCode, String locationName, String note, String status,
		BigDecimal totalReceivedQuantity, BigDecimal acceptedQuantity, BigDecimal rejectedQuantity,
		long version, Instant createdAt, List<Line> lines) {

	public record Line(
			UUID id, int lineNumber, UUID purchaseOrderLineId, UUID materialId, String materialCode,
			String materialName, String materialSpecification, String unit, BigDecimal receivedQuantity,
			boolean inspectionRequired, String lotNumber, String status, UUID inspectionId, String inspectionNumber,
			BigDecimal acceptedQuantity, BigDecimal rejectedQuantity, UUID acceptedBalanceId,
			UUID rejectedBalanceId, String stockSummary, long version) { }

	public record CreateRequest(
			@NotNull UUID purchaseOrderId,
			@NotNull UUID warehouseId,
			@NotNull UUID locationId,
			@Size(max = 500) String note,
			@Valid @NotNull @Size(min = 1, max = 100) List<LineInput> lines) { }

	public record LineInput(
			@NotNull UUID orderLineId,
			@NotNull @Positive BigDecimal receivedQuantity,
			@NotBlank @Size(max = 80) String lotNumber) { }
}
