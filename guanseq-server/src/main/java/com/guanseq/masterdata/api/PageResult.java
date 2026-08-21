package com.guanseq.masterdata.api;

import java.util.List;

public record PageResult<T>(
		List<T> items,
		long totalElements,
		int page,
		int size,
		int totalPages) {
}
