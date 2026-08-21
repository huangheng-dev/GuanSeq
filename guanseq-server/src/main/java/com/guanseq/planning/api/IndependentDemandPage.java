package com.guanseq.planning.api;

import java.util.List;

public record IndependentDemandPage(
		List<IndependentDemandRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) {
}
