package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdvanceRecord(
		UUID id,
		String advanceNumber,
		String type,
		String partyType,
		UUID partyId,
		String partyCode,
		String partyName,
		String currency,
		LocalDate advanceDate,
		BigDecimal totalAmount,
		BigDecimal appliedAmount,
		BigDecimal refundedAmount,
		BigDecimal availableBalance,
		String status,
		String note,
		long version,
		Instant createdAt,
		List<ApplicationRecord> applications,
		List<RefundRecord> refunds) {

	public record ApplicationRecord(
			UUID id,
			UUID invoiceId,
			String invoiceNumber,
			BigDecimal appliedAmount,
			LocalDate applicationDate,
			Instant createdAt) { }

	public record RefundRecord(
			UUID id,
			BigDecimal refundAmount,
			LocalDate refundDate,
			String reason,
			Instant createdAt) { }

	public record CreateRequest(
			@NotNull String type,
			@NotNull UUID partyId,
			@NotNull LocalDate advanceDate,
			@NotNull @DecimalMin(value = "0.01", message = "预收预付金额必须大于 0") BigDecimal totalAmount,
			@Size(max = 500) String note) { }

	public record RefundRequest(
			@NotNull @DecimalMin(value = "0.01", message = "退款金额必须大于 0") BigDecimal refundAmount,
			@NotNull LocalDate refundDate,
			@NotNull @Size(min = 4, max = 500, message = "退款原因至少 4 个字符") String reason) { }
}
