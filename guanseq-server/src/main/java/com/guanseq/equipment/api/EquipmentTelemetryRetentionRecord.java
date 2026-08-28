package com.guanseq.equipment.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EquipmentTelemetryRetentionRecord(
		UUID id,
		int retentionDays,
		long expiredSampleCount,
		Instant cutoffAt,
		long version,
		boolean defaultPolicy,
		boolean canManage,
		boolean schedulerAvailable,
		boolean automaticCleanupEnabled,
		int cleanupIntervalHours,
		Instant nextCleanupAt,
		String lastAutomationStatus,
		Instant lastAutomationCompletedAt,
		int consecutiveFailures,
		UUID updatedBy,
		Instant updatedAt,
		List<Event> events,
		List<AutomationRun> automationRuns) {

	public record Event(
			UUID id,
			UUID actorUserId,
			String action,
			int fromRetentionDays,
			int toRetentionDays,
			Boolean fromAutomaticCleanupEnabled,
			Boolean toAutomaticCleanupEnabled,
			Integer fromCleanupIntervalHours,
			Integer toCleanupIntervalHours,
			Instant cutoffAt,
			long deletedSampleCount,
			String reason,
			String requestId,
			Instant occurredAt) { }

	public record UpdateRequest(
			@Min(7) @Max(3650) int retentionDays,
			@PositiveOrZero long expectedVersion,
			@NotBlank @Size(min = 4, max = 500) String reason,
			Boolean automaticCleanupEnabled,
			@Min(1) @Max(720) Integer cleanupIntervalHours) { }

	public record CleanupRequest(
			@PositiveOrZero long expectedVersion,
			@NotBlank @Size(min = 4, max = 500) String reason) { }

	public record CleanupResult(
			EquipmentTelemetryRetentionRecord policy,
			long deletedSampleCount,
			Instant cutoffAt,
			String requestId,
			Instant occurredAt,
			boolean replayed) { }

	public record RunNowRequest(
			@PositiveOrZero long expectedVersion,
			@NotBlank @Size(min = 4, max = 500) String reason) { }

	public record AcknowledgeFailureRequest(
			@NotBlank @Size(min = 4, max = 500) String note) { }

	public record AutomationRun(
			UUID id,
			String triggerType,
			String status,
			UUID initiatedBy,
			String instanceId,
			String requestId,
			String reason,
			Instant cutoffAt,
			long deletedSampleCount,
			long remainingExpiredCount,
			String failureCode,
			String failureSummary,
			String attentionStatus,
			List<String> responsibleRoles,
			UUID acknowledgedBy,
			Instant acknowledgedAt,
			String acknowledgementNote,
			Instant startedAt,
			Instant completedAt) { }

	public record AutomationActionResult(
			EquipmentTelemetryRetentionRecord policy,
			AutomationRun run,
			boolean replayed) { }
}
