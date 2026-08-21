package com.guanseq.product.api;

import java.util.List;

public record RoutingPage(List<RoutingRecord> items, long totalElements, int page, int size, int totalPages) {
}
