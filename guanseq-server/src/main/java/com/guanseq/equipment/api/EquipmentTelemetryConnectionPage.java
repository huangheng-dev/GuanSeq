package com.guanseq.equipment.api;

import java.util.List;

public record EquipmentTelemetryConnectionPage(
		List<EquipmentTelemetryConnectionRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages,
		boolean canManage) { }
