package com.guanseq.equipment.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EquipmentTelemetryFieldAcceptanceRecord(
		UUID id, String acceptanceNumber, UUID connectionId, String status,
		boolean networkApproved, boolean securityValidated, boolean readOnlyConfirmed,
		boolean disconnectRecoveryVerified, boolean capacityVerified, boolean pointMappingApproved,
		String responsibleOwner, Instant testWindowStart, Instant testWindowEnd,
		String evidenceReference, String notes, String rejectionReason, long version,
		UUID createdBy, Instant createdAt, UUID submittedBy, Instant submittedAt,
		UUID approvedBy, Instant approvedAt, UUID rejectedBy, Instant rejectedAt, Instant updatedAt,
		List<String> availableActions, List<Event> events) {

	public record Context(
			UUID connectionId, String connectionCode, String connectionName, String protocol, String endpointType,
			boolean fieldEligible, boolean latestTechnicalPrecheckPassed, boolean fieldAccepted,
			boolean canMaintain, boolean canApprove, EquipmentTelemetryFieldAcceptanceRecord acceptance) { }

	public record Event(UUID id, UUID actorUserId, String action, String fromStatus, String toStatus,
			String reason, String requestId, Map<String, Object> details, Instant occurredAt) { }

	public record SaveRequest(
			@NotNull Boolean networkApproved,
			@NotNull Boolean securityValidated,
			@NotNull Boolean readOnlyConfirmed,
			@NotNull Boolean disconnectRecoveryVerified,
			@NotNull Boolean capacityVerified,
			@NotNull Boolean pointMappingApproved,
			@Size(max = 80) String responsibleOwner,
			Instant testWindowStart,
			Instant testWindowEnd,
			@Size(max = 240) String evidenceReference,
			@Size(max = 1000) String notes,
			@PositiveOrZero Long expectedVersion,
			@NotBlank @Size(min = 4, max = 500) String reason) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "SUBMIT|APPROVE|REJECT") String action,
			@NotBlank @Size(min = 4, max = 500) String reason,
			@NotNull @PositiveOrZero Long expectedVersion) { }
}
