package com.guanseq.equipment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EquipmentOeeRecord(
		UUID id, String recordNumber, UUID assetId, String assetCode, String assetName,
		String workCenterCode, String workCenterName, String location,
		Instant windowStart, Instant windowEnd, BigDecimal plannedProductionMinutes,
		BigDecimal downtimeMinutes, BigDecimal runMinutes, BigDecimal idealCycleSeconds,
		long totalCount, long goodCount, BigDecimal availabilityRate, BigDecimal performanceRate,
		BigDecimal qualityRate, BigDecimal oeeRate, String shiftName, String productionReference,
		String sourceType, String sourceReference, String status, String rejectionReason, long version,
		UUID createdBy, Instant createdAt, UUID submittedBy, Instant submittedAt,
		UUID approvedBy, Instant approvedAt, UUID rejectedBy, Instant rejectedAt, Instant updatedAt,
		List<String> availableActions, List<Downtime> downtimes, List<Event> events) {

	public record Downtime(UUID id, Instant startedAt, Instant endedAt, BigDecimal durationMinutes,
			String reasonCategory, String responsibleParty, String description,
			UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt) { }

	public record Event(UUID id, UUID actorUserId, String action, String fromStatus, String toStatus,
			String reason, String requestId, Map<String, Object> details, Instant occurredAt) { }

	public record CreateRequest(
			@NotNull UUID assetId,
			@NotNull Instant windowStart,
			@NotNull Instant windowEnd,
			@NotNull @DecimalMin(value = "0.01") BigDecimal plannedProductionMinutes,
			@NotNull @DecimalMin(value = "0.0001") BigDecimal idealCycleSeconds,
			@PositiveOrZero long totalCount,
			@PositiveOrZero long goodCount,
			@NotBlank @Size(max = 80) String shiftName,
			@Size(max = 120) String productionReference,
			@Size(max = 160) String sourceReference,
			@NotBlank @Size(min = 4, max = 500) String reason) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "UPDATE|ADD_DOWNTIME|UPDATE_DOWNTIME|REMOVE_DOWNTIME|SUBMIT|APPROVE|REJECT") String action,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion,
			Instant windowStart,
			Instant windowEnd,
			@DecimalMin(value = "0.01") BigDecimal plannedProductionMinutes,
			@DecimalMin(value = "0.0001") BigDecimal idealCycleSeconds,
			@PositiveOrZero Long totalCount,
			@PositiveOrZero Long goodCount,
			@Size(max = 80) String shiftName,
			@Size(max = 120) String productionReference,
			@Size(max = 160) String sourceReference,
			UUID downtimeId,
			Instant downtimeStartedAt,
			Instant downtimeEndedAt,
			@Pattern(regexp = "EQUIPMENT_FAILURE|SETUP_CHANGEOVER|MATERIAL_WAIT|QUALITY_HOLD|PERSONNEL_WAIT|PLANNED_MAINTENANCE|OTHER") String reasonCategory,
			@Size(max = 80) String responsibleParty,
			@Size(max = 500) String description) { }
}
