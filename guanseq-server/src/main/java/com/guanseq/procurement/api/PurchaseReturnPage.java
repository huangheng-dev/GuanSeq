package com.guanseq.procurement.api;

import java.util.List;

public record PurchaseReturnPage(List<PurchaseReturnRecord> items, long totalElements, int page, int size,
		int totalPages, boolean canCreate) { }
