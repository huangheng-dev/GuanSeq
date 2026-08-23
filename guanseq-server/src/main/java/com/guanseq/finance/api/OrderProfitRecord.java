package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderProfitRecord(
		UUID id,
		String settlementNumber,
		UUID salesOrderId,
		String orderNumber,
		UUID customerId,
		String customerCode,
		String customerName,
		String currency,
		String orderStatus,
		BigDecimal orderedQuantity,
		BigDecimal shippedQuantity,
		BigDecimal revenue,
		BigDecimal materialCost,
		BigDecimal laborCost,
		BigDecimal overheadCost,
		BigDecimal processingCost,
		BigDecimal totalCost,
		BigDecimal grossProfit,
		BigDecimal grossMargin,
		String costBasis,
		String costStatus,
		String status,
		Integer settlementVersion,
		UUID supersedesId,
		String impactReason,
		List<String> missingItems,
		long version,
		Instant settledAt,
		List<Line> lines) {

	public record Line(
			UUID id,
			UUID salesOrderLineId,
			int lineNumber,
			UUID productionOrderId,
			String productionOrderNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal orderedQuantity,
			BigDecimal shippedQuantity,
			BigDecimal acceptedQuantity,
			BigDecimal consumedQuantity,
			BigDecimal unitPrice,
			BigDecimal revenue,
			BigDecimal materialCost,
			BigDecimal laborCost,
			BigDecimal overheadCost,
			BigDecimal processingCost,
			BigDecimal totalCost,
			BigDecimal grossProfit,
			BigDecimal grossMargin,
			String costStatus,
			List<String> missingItems) { }
}
