package com.guanseq.equipment.api;

import java.util.List;

public record EquipmentAlertPage(List<EquipmentAlertRecord> items, long totalElements, int page, int size,
		int totalPages, long activeConditionCount, long unclosedCount, boolean canManage) { }
