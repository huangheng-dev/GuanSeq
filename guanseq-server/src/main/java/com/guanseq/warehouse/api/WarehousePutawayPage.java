package com.guanseq.warehouse.api;

import java.util.List;

public record WarehousePutawayPage(List<WarehousePutawayRecord> items, long totalElements, int page, int size, int totalPages) { }

