package com.guanseq.finance.api;

import java.util.List;

public record AdvancePage(
		List<AdvanceRecord> items,
		long totalElements,
		int totalPages,
		int page,
		int size) { }
