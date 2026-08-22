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

public record ReceivableCreditNoteRecord(
		UUID id,
		String creditNoteNumber,
		UUID originalInvoiceId,
		String originalInvoiceNumber,
		UUID salesOrderId,
		String orderNumber,
		UUID customerId,
		String customerCode,
		String customerName,
		String currency,
		String taxNoticeNumber,
		LocalDate creditNoteDate,
		LocalDate dueDate,
		BigDecimal taxRate,
		BigDecimal netAmount,
		BigDecimal taxAmount,
		BigDecimal grossAmount,
		String reason,
		String status,
		long version,
		Instant createdAt,
		List<Line> lines) {

	public record Line(
			UUID id,
			UUID originalInvoiceLineId,
			UUID salesOrderLineId,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal creditQuantity,
			BigDecimal unitPrice,
			BigDecimal netAmount,
			BigDecimal taxAmount,
			BigDecimal grossAmount) { }

	public record CreateRequest(
			@NotNull UUID originalInvoiceId,
			@Size(max = 80) String taxNoticeNumber,
			@NotNull LocalDate creditNoteDate,
			@NotNull LocalDate dueDate,
			@NotNull @Size(min = 4, max = 500) String reason,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines) { }

	public record LineInput(
			@NotNull UUID originalInvoiceLineId,
			@NotNull @DecimalMin(value = "0.0001") BigDecimal creditQuantity,
			BigDecimal unitPrice) { }

	public record RefundRequest(
			@PositiveOrZero long expectedVersion,
			@NotNull LocalDate refundDate,
			@NotNull @DecimalMin(value = "0.01") BigDecimal amount,
			@NotNull @Pattern(regexp = "BANK_TRANSFER|CASH|BILL|OTHER") String paymentMethod,
			@Size(max = 120) String bankReference,
			@Size(max = 500) String note) { }

	public record ReverseRequest(
			@NotNull LocalDate reversalDate,
			@NotNull @Size(min = 4, max = 500) String reason) { }
}
