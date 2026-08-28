package com.guanseq.warehouse.api;

import java.util.List;

public record WarehouseStockCountPage(List<WarehouseStockCountRecord> items,long totalElements,int page,int size,int totalPages) { }
