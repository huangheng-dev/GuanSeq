package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PayableReferenceData(List<InvoiceableOrder> orders) {

	public record InvoiceableOrder(
			UUID purchaseOrderId,
			String orderNumber,
			UUID supplierId,
			String supplierCode,
			String supplierName,
			String currency,
			BigDecimal taxRate,
			String orderStatus,
			BigDecimal acceptedAmount,
			BigDecimal invoicedAmount,
			BigDecimal remainingAmount,
			List<InvoiceableLine> lines) { }

	public record InvoiceableLine(
			UUID purchaseOrderLineId,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal acceptedQuantity,
			BigDecimal invoicedQuantity,
			BigDecimal remainingQuantity,
			BigDecimal unitPrice) { }
}
