package com.guanseq.production.api;

import java.util.List;

public record ProductionWorkReportPage(List<ProductionWorkReportRecord> items, long totalElements, int page, int size,
		int totalPages) { }
