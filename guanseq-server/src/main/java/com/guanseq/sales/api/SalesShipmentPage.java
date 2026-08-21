package com.guanseq.sales.api;

import java.util.List;

public record SalesShipmentPage(List<SalesShipmentRecord> items, long totalElements, int page, int size, int totalPages) {
}
