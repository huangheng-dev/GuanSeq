package com.guanseq.procurement.api;

import java.util.List;

public record SupplierPage(List<SupplierRecord> items, long totalElements, int totalPages, int page, int size) { }
