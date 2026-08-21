package com.guanseq.product.api;

import java.util.List;

public record BomPage(
		List<BomRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) {
}
