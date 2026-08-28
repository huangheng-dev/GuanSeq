package com.guanseq.equipment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EquipmentAlertRuleRecord(
		UUID id, String ruleCode, String name, UUID connectionId, String connectionCode, String connectionName,
		UUID assetId, String assetCode, String assetName, UUID pointId, String pointCode, String pointName,
		String ruleType, BigDecimal thresholdValue, String severity, String defaultAssignee, String status,
		long version, Instant createdAt, Instant updatedAt, List<String> availableActions, List<Event> events) {

	public record Event(UUID id, UUID actorUserId, String action, String fromStatus, String toStatus,
			String reason, String requestId, Map<String, Object> details, Instant occurredAt) { }

	public record CreateRequest(
			@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String ruleCode,
			@NotBlank @Size(max = 120) String name,
			@NotNull UUID connectionId,
			UUID pointId,
			@NotNull @Pattern(regexp = "HIGH_LIMIT|LOW_LIMIT|COMMUNICATION_FAILURE") String ruleType,
			BigDecimal thresholdValue,
			@NotNull @Pattern(regexp = "WARNING|CRITICAL") String severity,
			@NotBlank @Size(max = 80) String defaultAssignee,
			@NotBlank @Size(min = 4, max = 500) String reason) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "ACTIVATE|PAUSE") String action,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion) { }
}
