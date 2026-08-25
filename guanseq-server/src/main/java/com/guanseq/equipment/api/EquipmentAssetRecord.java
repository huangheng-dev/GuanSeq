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

public record EquipmentAssetRecord(
		UUID id,
		String assetCode,
		String assetName,
		String category,
		String manufacturer,
		String model,
		String serialNumber,
		String workCenterCode,
		String workCenterName,
		String location,
		String responsiblePerson,
		LocalDate commissioningDate,
		String operatingStatus,
		Instant statusChangedAt,
		long version,
		Instant createdAt,
		Instant updatedAt,
		List<Event> events) {

	public record Event(UUID id, UUID actorUserId, String action, String fromStatus, String toStatus,
			String reason, String requestId, Map<String, Object> details, Instant occurredAt) { }

	public record CreateRequest(
			@NotBlank @Size(max = 40) String assetCode,
			@NotBlank @Size(max = 120) String assetName,
			@NotNull @Pattern(regexp = "PRODUCTION|QUALITY|UTILITY|LOGISTICS|OTHER") String category,
			@Size(max = 120) String manufacturer,
			@Size(max = 120) String model,
			@Size(max = 120) String serialNumber,
			@Size(max = 40) String workCenterCode,
			@Size(max = 120) String workCenterName,
			@NotBlank @Size(max = 160) String location,
			@NotBlank @Size(max = 80) String responsiblePerson,
			LocalDate commissioningDate,
			@NotBlank @Size(min = 4, max = 500) String reason) { }

	public record UpdateRequest(
			@NotBlank @Size(max = 120) String assetName,
			@NotNull @Pattern(regexp = "PRODUCTION|QUALITY|UTILITY|LOGISTICS|OTHER") String category,
			@Size(max = 120) String manufacturer,
			@Size(max = 120) String model,
			@Size(max = 120) String serialNumber,
			@Size(max = 40) String workCenterCode,
			@Size(max = 120) String workCenterName,
			@NotBlank @Size(max = 160) String location,
			@NotBlank @Size(max = 80) String responsiblePerson,
			LocalDate commissioningDate,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@PositiveOrZero long expectedVersion) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "START|STOP|REPORT_BREAKDOWN|START_MAINTENANCE|COMPLETE_MAINTENANCE|INACTIVATE") String action,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@PositiveOrZero long expectedVersion) { }
}
