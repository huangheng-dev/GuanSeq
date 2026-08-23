package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GrirAccrualPreview(
		int fiscalYear,
		int fiscalPeriod,
		UUID priorAccrualId,
		String priorAccrualNumber,
		BigDecimal priorAccrualAmount,
		BigDecimal totalNetAmount,
		List<Line> lines) {

	public record Line(
			UUID purchaseOrderId,
			String orderNumber,
			UUID supplierId,
			String supplierCode,
			String supplierName,
			UUID purchaseOrderLineId,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal receivedQuantity,
			BigDecimal invoicedQuantity,
			BigDecimal accruedQuantity,
			BigDecimal unitPrice,
			BigDecimal netAmount) { }
}
