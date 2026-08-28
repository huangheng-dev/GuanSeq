package com.guanseq.warehouse.api;

import java.util.List;

public record WarehouseTransferPage(List<WarehouseTransferRecord> items,long totalElements,int page,int size,int totalPages) { }
