package com.guanseq.production.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductionOrderRecord(
		UUID id, String orderNumber, UUID materialId, String materialCode, String materialName,
		String materialSpecification, String unit, BigDecimal plannedQuantity, BigDecimal completedQuantity,
		BigDecimal reportedQuantity, BigDecimal reportableQuantity, BigDecimal outstandingQuantity,
		LocalDate plannedStartDate, LocalDate plannedReceiptDate,
		String workshop, String owner, String sourceType, UUID sourceId, String sourceNumber,
		String status, String cancellationReason, long version, Instant updatedAt) {

	public record CreateRequest(@NotNull UUID materialId,
			@NotNull @DecimalMin("0.0001") BigDecimal plannedQuantity,
			@NotNull LocalDate plannedStartDate, @NotNull LocalDate plannedReceiptDate,
			@NotBlank @Size(max = 120) String workshop,
			@NotBlank @Size(max = 80) String owner,
			@NotNull @Pattern(regexp = "MANUAL|MRP|SALES_ORDER") String sourceType,
			UUID sourceId, @Size(max = 60) String sourceNumber) { }

	public record UpdateRequest(@NotNull UUID materialId,
			@NotNull @DecimalMin("0.0001") BigDecimal plannedQuantity,
			@NotNull LocalDate plannedStartDate, @NotNull LocalDate plannedReceiptDate,
			@NotBlank @Size(max = 120) String workshop,
			@NotBlank @Size(max = 80) String owner,
			@NotNull @Pattern(regexp = "MANUAL|MRP|SALES_ORDER") String sourceType,
			UUID sourceId, @Size(max = 60) String sourceNumber, long expectedVersion) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "RELEASE|START|CANCEL") String action,
			long expectedVersion, @Size(max = 500) String comment) { }
}
