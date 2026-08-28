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

public record EquipmentAlertRecord(
		UUID id, String alertNumber, UUID ruleId, String ruleCode, String ruleName,
		UUID assetId, String assetCode, String assetName, UUID connectionId, String connectionCode,
		UUID pointId, String pointCode, String pointName, String ruleType, String severity, String status,
		boolean conditionActive, BigDecimal observedValue, String observedQuality, String failureCode,
		String assignee, String resolutionNotes, UUID linkedWorkOrderId, String linkedWorkOrderNumber,
		long version, Instant firstOccurredAt, Instant lastOccurredAt, Instant recoveredAt,
		Instant acknowledgedAt, Instant processingStartedAt, Instant resolvedAt, Instant closedAt,
		Instant updatedAt, List<String> availableActions, List<Event> events) {

	public record Event(UUID id, UUID actorUserId, String action, String fromStatus, String toStatus,
			String reason, String requestId, Map<String, Object> details, Instant occurredAt) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "ACKNOWLEDGE|START_PROCESSING|RESOLVE|CLOSE|LINK_REPAIR") String action,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion,
			@Size(max = 80) String assignee,
			@Size(max = 1000) String resolutionNotes,
			UUID workOrderId) { }
}
