package com.guanseq.production.api;

import java.util.List;
import java.util.UUID;

public record OperationTaskPage(
		List<OperationTaskRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) { }
