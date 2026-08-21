package com.guanseq.planning.api;

import java.util.List;

public record MrpRunPage(
		List<MrpRunRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) {
}
