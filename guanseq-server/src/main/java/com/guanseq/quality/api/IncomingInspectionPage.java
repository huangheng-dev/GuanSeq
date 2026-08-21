package com.guanseq.quality.api;

import java.util.List;

public record IncomingInspectionPage(List<IncomingInspectionRecord> items, long totalElements, int page, int size,
		int totalPages) {
}
