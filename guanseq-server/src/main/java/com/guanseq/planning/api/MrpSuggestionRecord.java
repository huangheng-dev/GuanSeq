package com.guanseq.planning.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MrpSuggestionRecord(
		UUID id, UUID runId, String runNumber, String runName,
		int requirementLevel, String sourceType, String parentMaterialCode,
		UUID materialId, String materialCode, String materialName, String procurementType, String unit,
		BigDecimal grossQuantity, BigDecimal netQuantity, LocalDate requiredDate, LocalDate recommendedReleaseDate,
		String recommendationType, String decisionStatus, String decisionComment, Instant decidedAt,
		String convertedOrderType, UUID convertedOrderId, String convertedOrderNumber, Instant convertedAt,
		long version, Instant createdAt) {

	public record ActionRequest(
			@NotNull @Pattern(regexp = "APPROVE|REJECT") String action,
			long expectedVersion, @Size(max = 500) String comment) { }

	public record ConvertRequest(
			long expectedVersion,
			UUID supplierId, String currency, BigDecimal taxRate, BigDecimal unitPrice,
			LocalDate requestedReceiptDate, @Size(max = 80) String buyer,
			LocalDate plannedStartDate, LocalDate plannedReceiptDate,
			@Size(max = 120) String workshop, @Size(max = 80) String owner) { }
}
