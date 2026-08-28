package com.guanseq.equipment.api;

import java.util.List;

public record EquipmentMaintenancePlanPage(
		List<EquipmentMaintenancePlanRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages,
		long activeCount,
		long generationDueCount,
		long overdueWorkOrderCount,
		boolean canMaintain,
		List<EquipmentMaintenanceGenerationRecord> recentRuns) { }
