package com.guanseq.equipment.api;

import java.util.List;

public record EquipmentWorkOrderPage(List<EquipmentWorkOrderRecord> items, long totalElements, int page, int size,
		int totalPages, boolean canMaintain) { }
