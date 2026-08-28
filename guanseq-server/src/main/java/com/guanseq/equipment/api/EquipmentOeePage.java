package com.guanseq.equipment.api;

import java.math.BigDecimal;
import java.util.List;

public record EquipmentOeePage(
		List<EquipmentOeeRecord> items, long totalElements, int page, int size, int totalPages,
		long approvedRecordCount, BigDecimal averageAvailabilityRate, BigDecimal averagePerformanceRate,
		BigDecimal averageQualityRate, BigDecimal averageOeeRate, boolean canMaintain, boolean canApprove) { }
