package com.guanseq.sales.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalesShipmentRecord(
		UUID id,
		String shipmentNumber,
		UUID salesOrderId,
		String orderNumber,
		UUID customerId,
		String customerCode,
		String customerName,
		UUID warehouseId,
		String warehouseCode,
		String warehouseName,
		LocalDate plannedShippingDate,
		Instant actualShippedAt,
		String status,
		String note,
		BigDecimal totalShippedQuantity,
		long version,
		Instant createdAt,
		List<Line> lines) {

	public record Line(
			UUID id,
			UUID orderLineId,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal shippedQuantity,
			String stockSummary) {
	}

	public record CreateRequest(
			@NotNull UUID salesOrderId,
			@NotNull UUID warehouseId,
			@NotNull LocalDate plannedShippingDate,
			@Size(max = 500) String note,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines) {
	}

	public record LineInput(
			@NotNull UUID orderLineId,
			@NotNull @DecimalMin(value = "0.0001") BigDecimal shippedQuantity) {
	}
}