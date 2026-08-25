package com.guanseq.equipment.api;

import java.util.List;

public record EquipmentSparePartPage(List<EquipmentSparePartRecord> items, long totalElements, int page, int size,
		int totalPages, boolean canMaintain) { }
