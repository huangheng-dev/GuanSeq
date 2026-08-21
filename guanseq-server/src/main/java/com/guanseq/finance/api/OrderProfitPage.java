package com.guanseq.finance.api;

import java.util.List;

public record OrderProfitPage(List<OrderProfitRecord> items, long totalElements, int page, int size, int totalPages) { }
