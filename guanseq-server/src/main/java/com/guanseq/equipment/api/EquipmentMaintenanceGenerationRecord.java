package com.guanseq.equipment.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EquipmentMaintenanceGenerationRecord(
		UUID id,
		String requestId,
		LocalDate asOfDate,
		String reason,
		String status,
		int generatedCount,
		int existingCount,
		int skippedCount,
		UUID actorUserId,
		Instant startedAt,
		Instant completedAt,
		List<Item> items) {

	public record Item(UUID id, UUID planId, LocalDate dueDate, String outcome, UUID workOrderId, String message) { }
}
