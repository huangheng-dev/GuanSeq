package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PayableInvoiceRecord(
		UUID id,
		String invoiceNumber,
		String supplierInvoiceNumber,
		UUID purchaseOrderId,
		String orderNumber,
		UUID supplierId,
		String supplierCode,
		String supplierName,
		String currency,
		LocalDate invoiceDate,
		LocalDate dueDate,
		BigDecimal taxRate,
		BigDecimal netAmount,
		BigDecimal taxAmount,
		BigDecimal grossAmount,
		BigDecimal paidAmount,
		BigDecimal outstandingAmount,
		BigDecimal creditBalance,
		String status,
		String purchaseReturnImpactStatus,
		long version,
		Instant createdAt,
		List<Line> lines,
		List<Payment> payments) {

	public record Line(
			UUID id,
			UUID purchaseOrderLineId,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal invoiceQuantity,
			BigDecimal unitPrice,
			BigDecimal netAmount,
			BigDecimal taxAmount,
			BigDecimal grossAmount) { }

	public record Payment(
			UUID id,
			String paymentNumber,
			String direction,
			BigDecimal amount,
			LocalDate paymentDate,
			String paymentMethod,
			String bankReference,
			String note,
			String status,
			Instant createdAt) { }

	public record CreateRequest(
			@NotNull UUID purchaseOrderId,
			@NotBlank @Size(max = 80) String supplierInvoiceNumber,
			@NotNull LocalDate invoiceDate,
			@NotNull LocalDate dueDate,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines,
			UUID advanceId) { }

	public record LineInput(
			@NotNull UUID purchaseOrderLineId,
			@NotNull @DecimalMin(value = "0.0001") BigDecimal invoiceQuantity) { }

	public record PaymentRequest(
			@PositiveOrZero long expectedVersion,
			@NotNull LocalDate paymentDate,
			@NotNull @DecimalMin(value = "0.01") BigDecimal amount,
			@NotNull @Pattern(regexp = "BANK_TRANSFER|CASH|BILL|OTHER") String paymentMethod,
			@Size(max = 120) String bankReference,
			@Size(max = 500) String note) { }
}
