package com.guanseq.production.api;

import java.util.List;

public record OperationLaborEntryPage(
		List<OperationLaborEntryRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) { }
