package com.guanseq.finance.api;

import java.util.List;

public record GrirAccrualPage(
		List<GrirAccrualRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) { }
