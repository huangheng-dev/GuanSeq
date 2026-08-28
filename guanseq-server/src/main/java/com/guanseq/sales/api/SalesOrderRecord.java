package com.guanseq.sales.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SalesOrderRecord(
		UUID id,
		String orderNumber,
		UUID customerId,
		String customerCode,
		String customerName,
		String currency,
		BigDecimal taxRate,
		LocalDate requestedDeliveryDate,
		LocalDate promisedDeliveryDate,
		String owner,
		String status,
		BigDecimal totalNetAmount,
		BigDecimal totalTaxAmount,
		BigDecimal totalGrossAmount,
		String rejectionReason,
		long version,
		Instant updatedAt,
		List<Line> lines) {

	public record Line(
			UUID id,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal netAmount,
			BigDecimal taxAmount,
			BigDecimal grossAmount,
		BigDecimal deliveredQuantity,
		BigDecimal returnedQuantity,
		BigDecimal netDeliveredQuantity) {
	}

	public record LineInput(
			@NotNull UUID materialId,
			@NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
			@NotNull @DecimalMin(value = "0.0000") BigDecimal unitPrice) {
	}

	public record CreateRequest(
			@NotNull UUID customerId,
			@NotNull @Pattern(regexp = "CNY|USD|EUR") String currency,
			@NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal taxRate,
			@NotNull LocalDate requestedDeliveryDate,
			LocalDate promisedDeliveryDate,
			@NotBlank @Size(max = 80) String owner,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines) {
	}

	public record UpdateRequest(
			@NotNull UUID customerId,
			@NotNull @Pattern(regexp = "CNY|USD|EUR") String currency,
			@NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal taxRate,
			@NotNull LocalDate requestedDeliveryDate,
			LocalDate promisedDeliveryDate,
			@NotBlank @Size(max = 80) String owner,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines,
			long expectedVersion) {
	}

	public record ActionRequest(
			@NotNull @Pattern(regexp = "SUBMIT|APPROVE|REJECT|RELEASE") String action,
			long expectedVersion,
			@Size(max = 500) String comment) {
	}
}
