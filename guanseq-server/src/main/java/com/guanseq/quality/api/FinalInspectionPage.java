package com.guanseq.quality.api;

import java.util.List;

public record FinalInspectionPage(List<FinalInspectionRecord> items, long totalElements, int page, int size,
		int totalPages) { }
