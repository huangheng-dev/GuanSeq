package com.guanseq.procurement.api;

import java.util.List;
import java.util.UUID;

public record PurchaseReceiptPage(List<PurchaseReceiptRecord> items, long totalElements, int page, int size,
		int totalPages) {
}
