package com.guanseq.finance.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountingPeriodRecord(
		UUID id,
		int fiscalYear,
		int fiscalPeriod,
		String periodLabel,
		String status,
		Instant closedAt,
		String closedByName,
		Instant reopenedAt,
		String reopenedByName,
		String reopenReason,
		long version,
		Instant createdAt,
		Instant updatedAt) {

	public record CreateRequest(
			@NotNull Integer fiscalYear,
			@NotNull Integer fiscalPeriod) { }

	public record ReopenRequest(
			@NotNull @Size(min = 4, max = 500) String reason,
			Long expectedVersion) { }
}
