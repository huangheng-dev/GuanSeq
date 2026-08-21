package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReceivableReferenceData(List<InvoiceableOrder> orders) {

	public record InvoiceableOrder(
			UUID salesOrderId,
			String orderNumber,
			UUID customerId,
			String customerCode,
			String customerName,
			String currency,
			BigDecimal taxRate,
			String orderStatus,
			BigDecimal deliveredAmount,
			BigDecimal invoicedAmount,
			BigDecimal remainingAmount,
			List<InvoiceableLine> lines) { }

	public record InvoiceableLine(
			UUID salesOrderLineId,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal deliveredQuantity,
			BigDecimal invoicedQuantity,
			BigDecimal remainingQuantity,
			BigDecimal unitPrice) { }
}
