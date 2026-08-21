package com.guanseq.planning.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MrpRunRecord(
		UUID id,
		String runNumber,
		String name,
		LocalDate horizonStart,
		LocalDate horizonEnd,
		String status,
		int demandCount,
		BigDecimal totalQuantity,
		int exceptionCount,
		Instant startedAt,
		Instant finishedAt,
		String requestId,
		long version,
		List<DemandSnapshot> demands,
		List<SupplySnapshot> supplies,
		List<ScheduledReceiptSnapshot> scheduledReceipts,
		List<NetRequirement> netRequirements,
		List<RunException> exceptions) {

	public record CreateRequest(
			@NotBlank @Size(max = 120) String name,
			@NotNull LocalDate horizonStart,
			@NotNull LocalDate horizonEnd) {
	}

	public record DemandSnapshot(
			UUID id,
			UUID demandId,
			String demandNumber,
			String sourceType,
			String sourceNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String procurementType,
			String unit,
			BigDecimal quantity,
			LocalDate requiredDate,
			String priority,
			String owner,
			Instant snapshottedAt) {
	}

	public record RunException(
			UUID id,
			String code,
			String severity,
			UUID materialId,
			String materialCode,
			String materialName,
			String message,
			String resolutionPath,
			Instant createdAt) {
	}

	public record SupplySnapshot(
			UUID id,
			UUID materialId,
			String materialCode,
			String materialName,
			String unit,
			BigDecimal onHandQuantity,
			BigDecimal allocatedQuantity,
			BigDecimal frozenQuantity,
			BigDecimal availableQuantity,
			int balanceCount,
			Instant snapshottedAt) {
	}

	public record ScheduledReceiptSnapshot(
			UUID id,
			String sourceType,
			UUID sourceOrderId,
			String sourceOrderNumber,
			UUID sourceLineId,
			String sourceName,
			UUID materialId,
			String materialCode,
			String materialName,
			String unit,
			BigDecimal outstandingQuantity,
			LocalDate expectedReceiptDate,
			Instant snapshottedAt) {
	}

	public record NetRequirement(
			UUID id, int requirementLevel, String sourceType, UUID parentMaterialId, String parentMaterialCode,
			UUID materialId, String materialCode, String materialName, String procurementType, String unit,
			BigDecimal grossQuantity, BigDecimal availableConsumed, BigDecimal scheduledReceiptConsumed,
			BigDecimal netQuantity, LocalDate requiredDate, LocalDate recommendedReleaseDate,
			String recommendationType, String decisionStatus, String convertedOrderType,
			UUID convertedOrderId, String convertedOrderNumber, long version, Instant createdAt) { }
}
