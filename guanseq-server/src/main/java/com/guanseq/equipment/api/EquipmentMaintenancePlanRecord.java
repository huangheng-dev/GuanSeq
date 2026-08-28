package com.guanseq.equipment.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EquipmentMaintenancePlanRecord(
		UUID id,
		String planCode,
		String name,
		String workType,
		UUID assetId,
		String assetCode,
		String assetName,
		String assetLocation,
		String description,
		String priority,
		int intervalDays,
		int leadDays,
		LocalDate firstDueDate,
		LocalDate nextDueDate,
		LocalDate nextGenerationDate,
		LocalTime plannedStartTime,
		LocalTime dueTime,
		String assignee,
		String status,
		String generationStatus,
		long overdueWorkOrderCount,
		List<String> overdueWorkOrderNumbers,
		long version,
		Instant createdAt,
		Instant updatedAt,
		List<String> availableActions,
		List<Event> events) {

	public record Event(UUID id, UUID actorUserId, String action, String fromStatus, String toStatus,
			String reason, String requestId, Map<String, Object> details, Instant occurredAt) { }

	public record CreateRequest(
			@NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{1,39}") String planCode,
			@NotBlank @Size(max = 160) String name,
			@NotNull @Pattern(regexp = "INSPECTION|PREVENTIVE_MAINTENANCE") String workType,
			@NotNull UUID assetId,
			@NotBlank @Size(min = 4, max = 1000) String description,
			@NotNull @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
			@Min(1) @Max(3650) int intervalDays,
			@Min(0) @Max(365) int leadDays,
			@NotNull LocalDate firstDueDate,
			@NotNull LocalTime plannedStartTime,
			@NotNull LocalTime dueTime,
			@NotBlank @Size(max = 80) String assignee,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long assetExpectedVersion) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "ACTIVATE|INACTIVATE") String action,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion) { }

	public record GenerateRequest(
			@NotNull LocalDate asOfDate,
			@NotBlank @Size(min = 4, max = 500) String reason) { }
}
