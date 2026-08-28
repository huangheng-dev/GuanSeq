package com.guanseq.equipment.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EquipmentWorkOrderRecord(
		UUID id,
		String workOrderNumber,
		String workType,
		String sourceType,
		UUID sourceWorkOrderId,
		UUID sourcePlanId,
		LocalDate sourceDueDate,
		UUID assetId,
		String assetCode,
		String assetName,
		String assetLocation,
		String assetOperatingStatus,
		long assetVersion,
		String title,
		String description,
		String priority,
		String status,
		Instant plannedStartAt,
		Instant dueAt,
		String assignee,
		String outcome,
		String completionNotes,
		Instant startedAt,
		Instant submittedAt,
		Instant completedAt,
		long version,
		Instant createdAt,
		Instant updatedAt,
		EquipmentMaintenanceCostRecord.CostEvidence costEvidence,
		List<String> availableActions,
		List<Event> events) {

	public record Event(UUID id, UUID actorUserId, String action, String fromStatus, String toStatus,
			String reason, String outcome, String requestId, Map<String, Object> details, Instant occurredAt) { }

	public record CreateRequest(
			@NotNull UUID assetId,
			@NotNull @Pattern(regexp = "INSPECTION|PREVENTIVE_MAINTENANCE|REPAIR") String workType,
			@NotBlank @Size(max = 160) String title,
			@NotBlank @Size(min = 4, max = 1000) String description,
			@NotNull @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
			@NotNull Instant plannedStartAt,
			@NotNull Instant dueAt,
			@NotBlank @Size(max = 80) String assignee,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long assetExpectedVersion) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "START|COMPLETE|SUBMIT_FOR_ACCEPTANCE|ACCEPT|REJECT|CANCEL") String action,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion,
			@NotNull @PositiveOrZero Long assetExpectedVersion,
			@Pattern(regexp = "PASS|FAIL") String outcome,
			@Size(max = 1000) String completionNotes) { }
}
