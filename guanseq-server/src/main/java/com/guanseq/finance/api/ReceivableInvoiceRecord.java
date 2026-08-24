package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReceivableInvoiceRecord(
		UUID id,
		String invoiceNumber,
		UUID salesOrderId,
		String orderNumber,
		UUID customerId,
		String customerCode,
		String customerName,
		String currency,
		LocalDate invoiceDate,
		LocalDate dueDate,
		BigDecimal taxRate,
		BigDecimal netAmount,
		BigDecimal taxAmount,
		BigDecimal grossAmount,
		BigDecimal receivedAmount,
		BigDecimal outstandingAmount,
		BigDecimal creditBalance,
		String status,
		long version,
		Instant createdAt,
		List<Line> lines,
		List<Receipt> receipts) {

	public record Line(
			UUID id,
			UUID salesOrderLineId,
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

	public record Receipt(
			UUID id,
			String receiptNumber,
			String direction,
			BigDecimal amount,
			LocalDate receiptDate,
			String paymentMethod,
			String bankReference,
			String note,
			String status,
			Instant createdAt) { }

	public record CreateRequest(
			@NotNull UUID salesOrderId,
			@NotNull LocalDate invoiceDate,
			@NotNull LocalDate dueDate,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines,
			UUID advanceId) { }

	public record LineInput(
			@NotNull UUID salesOrderLineId,
			@NotNull @DecimalMin(value = "0.0001") BigDecimal invoiceQuantity) { }

	public record ReceiptRequest(
			@PositiveOrZero long expectedVersion,
			@NotNull LocalDate receiptDate,
			@NotNull @DecimalMin(value = "0.01") BigDecimal amount,
			@NotNull @Pattern(regexp = "BANK_TRANSFER|CASH|BILL|OTHER") String paymentMethod,
			@Size(max = 120) String bankReference,
			@Size(max = 500) String note) { }
}
