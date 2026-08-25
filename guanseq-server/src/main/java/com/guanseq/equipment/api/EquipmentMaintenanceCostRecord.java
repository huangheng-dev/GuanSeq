package com.guanseq.equipment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class EquipmentMaintenanceCostRecord {
	private EquipmentMaintenanceCostRecord() { }

	public record CostEvidence(BigDecimal spareCost, BigDecimal laborCost, BigDecimal totalCost, String currency,
			String basis, List<SpareTransaction> spareTransactions, List<LaborTransaction> laborTransactions,
			List<String> availableActions) { }

	public record MutationResult(long workOrderVersion, CostEvidence costEvidence) { }

	public record SpareTransaction(UUID id, String transactionType, UUID returnOfIssueId, UUID sparePartId,
			String materialCode, String materialName, String materialSpecification, String unit, BigDecimal quantity,
			BigDecimal returnedQuantity, BigDecimal returnableQuantity, BigDecimal unitCost, String currency,
			BigDecimal amount, UUID warehouseId, String warehouseCode, String warehouseName,
			List<Map<String, Object>> warehouseEvidence, String reason, String requestId, UUID actorUserId,
			Instant occurredAt) { }

	public record LaborTransaction(UUID id, String transactionType, UUID reversalOfEntryId, String technicianName,
			BigDecimal hours, BigDecimal hourlyRate, String currency, BigDecimal amount, boolean reversed,
			String reason, String requestId, UUID actorUserId, Instant occurredAt) { }

	public record IssueRequest(@NotNull UUID sparePartId, @NotNull UUID warehouseId,
			@NotNull @DecimalMin("0.0001") BigDecimal quantity,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion) { }

	public record ReturnRequest(@NotNull UUID issueTransactionId, @NotNull UUID locationId,
			@NotNull @DecimalMin("0.0001") BigDecimal quantity,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion) { }

	public record LaborEntryRequest(@NotBlank @Size(max = 80) String technicianName,
			@NotNull @DecimalMin("0.01") @DecimalMax("24.00") BigDecimal hours,
			@NotNull @DecimalMin("0.000001") BigDecimal hourlyRate,
			@NotBlank @Size(min = 3, max = 3) String currency,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion) { }

	public record LaborReversalRequest(@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion) { }
}
