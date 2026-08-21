package com.guanseq.product.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BomRecord(
		UUID id,
		String bomNumber,
		UUID parentMaterialId,
		String parentMaterialCode,
		String parentMaterialName,
		String parentMaterialSpecification,
		String parentUnit,
		String usageType,
		String versionCode,
		BigDecimal baseQuantity,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		String owner,
		String changeReason,
		String status,
		long version,
		Instant updatedAt,
		Instant publishedAt,
		List<Line> lines,
		List<Event> events) {

	public record Line(
			UUID id,
			int lineNumber,
			UUID componentMaterialId,
			String componentMaterialCode,
			String componentMaterialName,
			String componentMaterialSpecification,
			String unit,
			BigDecimal quantity,
			BigDecimal scrapRate,
			String note) {
	}

	public record Event(UUID id, String action, String fromStatus, String toStatus, String requestId,
			Map<String, Object> details, Instant occurredAt) {
	}

	public record LineInput(
			@NotNull UUID componentMaterialId,
			@NotNull @Positive BigDecimal quantity,
			@NotNull @DecimalMin("0") @DecimalMax(value = "1", inclusive = false) BigDecimal scrapRate,
			@Size(max = 240) String note) {
	}

	public record CreateRequest(
			@NotNull UUID parentMaterialId,
			@NotNull @Pattern(regexp = "PRODUCTION") String usageType,
			@NotBlank @Size(max = 32) String versionCode,
			@NotNull @Positive BigDecimal baseQuantity,
			@NotNull LocalDate effectiveFrom,
			@NotBlank @Size(max = 80) String owner,
			@NotBlank @Size(max = 500) String changeReason,
			@NotEmpty @Size(max = 500) List<@Valid LineInput> lines) {
	}

	public record UpdateRequest(
			@NotNull UUID parentMaterialId,
			@NotNull @Pattern(regexp = "PRODUCTION") String usageType,
			@NotBlank @Size(max = 32) String versionCode,
			@NotNull @Positive BigDecimal baseQuantity,
			@NotNull LocalDate effectiveFrom,
			@NotBlank @Size(max = 80) String owner,
			@NotBlank @Size(max = 500) String changeReason,
			@NotEmpty @Size(max = 500) List<@Valid LineInput> lines,
			long expectedVersion) {
	}

	public record ActionRequest(
			@NotNull @Pattern(regexp = "PUBLISH|INACTIVATE") String action,
			long expectedVersion) {
	}
}
