package com.guanseq.planning.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IndependentDemandRecord(
		UUID id,
		String demandNumber,
		String sourceType,
		UUID sourceId,
		String sourceNumber,
		UUID sourceLineId,
		Integer sourceLineNumber,
		String sourceCustomer,
		UUID materialId,
		String materialCode,
		String materialName,
		String materialSpecification,
		String unit,
		BigDecimal quantity,
		LocalDate requiredDate,
		String priority,
		String owner,
		String status,
		String note,
		String cancellationReason,
		long version,
		Instant updatedAt) {

	public record CreateRequest(
			@NotNull UUID materialId,
			@NotNull @DecimalMin("0.0001") BigDecimal quantity,
			@NotNull LocalDate requiredDate,
			@NotNull @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
			@NotBlank @Size(max = 80) String owner,
			@Size(max = 500) String note) {
	}

	public record UpdateRequest(
			@NotNull UUID materialId,
			@NotNull @DecimalMin("0.0001") BigDecimal quantity,
			@NotNull LocalDate requiredDate,
			@NotNull @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
			@NotBlank @Size(max = 80) String owner,
			@Size(max = 500) String note,
			long expectedVersion) {
	}

	public record ActionRequest(
			@NotNull @Pattern(regexp = "ACTIVATE|CANCEL") String action,
			long expectedVersion,
			@Size(max = 500) String comment) {
	}
}
