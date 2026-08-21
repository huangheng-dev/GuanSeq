package com.guanseq.product.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoutingRecord(
		UUID id,
		String routingNumber,
		UUID materialId,
		String materialCode,
		String materialName,
		String materialSpecification,
		String materialUnit,
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
		List<Operation> operations,
		List<Event> events) {

	public record Operation(
			UUID id,
			int sequenceNumber,
			String operationCode,
			String operationName,
			String workCenterCode,
			String workCenterName,
			BigDecimal setupMinutes,
			BigDecimal runMinutesPerUnit,
			BigDecimal queueMinutes,
			boolean inspectionRequired,
			String instructionSummary) {
	}

	public record Event(UUID id, String action, String fromStatus, String toStatus, String requestId,
			Map<String, Object> details, Instant occurredAt) {
	}

	public record OperationInput(
			@NotBlank @Size(max = 40) String operationCode,
			@NotBlank @Size(max = 120) String operationName,
			@NotBlank @Size(max = 40) String workCenterCode,
			@NotBlank @Size(max = 120) String workCenterName,
			@NotNull @DecimalMin("0") BigDecimal setupMinutes,
			@NotNull @DecimalMin("0") BigDecimal runMinutesPerUnit,
			@NotNull @DecimalMin("0") BigDecimal queueMinutes,
			boolean inspectionRequired,
			@Size(max = 500) String instructionSummary) {
	}

	public record CreateRequest(
			@NotNull UUID materialId,
			@NotNull @Pattern(regexp = "PRODUCTION") String usageType,
			@NotBlank @Size(max = 32) String versionCode,
			@NotNull @Positive BigDecimal baseQuantity,
			@NotNull LocalDate effectiveFrom,
			@NotBlank @Size(max = 80) String owner,
			@NotBlank @Size(max = 500) String changeReason,
			@NotEmpty @Size(max = 100) List<@Valid OperationInput> operations) {
	}

	public record UpdateRequest(
			@NotNull UUID materialId,
			@NotNull @Pattern(regexp = "PRODUCTION") String usageType,
			@NotBlank @Size(max = 32) String versionCode,
			@NotNull @Positive BigDecimal baseQuantity,
			@NotNull LocalDate effectiveFrom,
			@NotBlank @Size(max = 80) String owner,
			@NotBlank @Size(max = 500) String changeReason,
			@NotEmpty @Size(max = 100) List<@Valid OperationInput> operations,
			long expectedVersion) {
	}

	public record ActionRequest(
			@NotNull @Pattern(regexp = "PUBLISH|INACTIVATE") String action,
			long expectedVersion) {
	}
}
