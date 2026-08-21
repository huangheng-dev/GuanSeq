package com.guanseq.sales.api;

import java.util.List;

public record SalesOrderPage(
		List<SalesOrderRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) {
}
