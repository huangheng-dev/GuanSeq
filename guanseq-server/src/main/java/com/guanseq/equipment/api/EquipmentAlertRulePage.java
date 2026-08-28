package com.guanseq.equipment.api;

import java.util.List;

public record EquipmentAlertRulePage(List<EquipmentAlertRuleRecord> items, long totalElements, int page, int size,
		int totalPages, boolean canManage) { }
