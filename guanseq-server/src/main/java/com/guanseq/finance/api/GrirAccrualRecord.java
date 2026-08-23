package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GrirAccrualRecord(
		UUID id,
		String accrualNumber,
		int fiscalYear,
		int fiscalPeriod,
		LocalDate accrualDate,
		String status,
		BigDecimal totalNetAmount,
		UUID reversedByAccrualId,
		LocalDate reversalDate,
		String reversalReason,
		String note,
		long version,
		Instant createdAt,
		List<Line> lines) {

	public record Line(
			UUID id,
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

	public record RunRequest(
			@NotNull @Min(2000) @Max(2100) Integer fiscalYear,
			@NotNull @Min(1) @Max(12) Integer fiscalPeriod,
			LocalDate accrualDate,
			@Size(max = 500) String note) { }

	public record ReverseRequest(
			@NotNull LocalDate reversalDate,
			@NotNull @Size(min = 4, max = 500) String reason) { }
}
