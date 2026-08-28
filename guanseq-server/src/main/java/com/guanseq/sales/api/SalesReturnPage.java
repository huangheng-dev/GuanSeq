package com.guanseq.sales.api;

import java.util.List;

public record SalesReturnPage(List<SalesReturnRecord> items, long totalElements, int page, int size, int totalPages,
		boolean canCreate) { }
