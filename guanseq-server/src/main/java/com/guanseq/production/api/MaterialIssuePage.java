package com.guanseq.production.api;

import java.util.List;

public record MaterialIssuePage(
		List<MaterialIssueRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) {
}
