package com.guanseq.equipment.api;

import java.util.List;

public record EquipmentAssetPage(List<EquipmentAssetRecord> items, long totalElements, int page, int size,
		int totalPages, boolean canMaintain) { }
