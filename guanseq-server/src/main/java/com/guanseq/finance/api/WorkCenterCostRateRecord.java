package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record WorkCenterCostRateRecord(
		UUID id,
		String workCenterCode,
		String workCenterName,
		String currency,
		BigDecimal laborRatePerHour,
		BigDecimal overheadRatePerHour,
		BigDecimal totalRatePerHour,
		LocalDate effectiveDate,
		String status,
		long version,
		Instant createdAt,
		Instant updatedAt) {

	public record CreateRequest(
			@NotBlank @Size(max = 40) String workCenterCode,
			@NotBlank @Size(max = 120) String workCenterName,
			@NotNull @Pattern(regexp = "CNY|USD|EUR") String currency,
			@NotNull @DecimalMin("0.000000") BigDecimal laborRatePerHour,
			@NotNull @DecimalMin("0.000000") BigDecimal overheadRatePerHour,
			@NotNull LocalDate effectiveDate) { }

	public record StatusRequest(
			@NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status,
			@PositiveOrZero long expectedVersion) { }
}
