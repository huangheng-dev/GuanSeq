package com.guanseq.production.api;

import java.util.List;

public record ProductionOrderPage(List<ProductionOrderRecord> items, long totalElements, int page, int size,
		int totalPages) { }
