package com.guanseq.finance.api;

import java.util.List;

public record WorkCenterCostRatePage(
		List<WorkCenterCostRateRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) { }
