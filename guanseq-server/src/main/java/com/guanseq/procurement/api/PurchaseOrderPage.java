package com.guanseq.procurement.api;

import java.util.List;

public record PurchaseOrderPage(List<PurchaseOrderRecord> items, long totalElements, int page, int size, int totalPages) {
}
