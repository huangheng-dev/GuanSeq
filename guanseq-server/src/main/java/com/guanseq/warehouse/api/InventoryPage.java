package com.guanseq.warehouse.api;

import java.util.List;

public record InventoryPage(List<InventoryRecord> items, long totalElements, int page, int size, int totalPages) {
}
