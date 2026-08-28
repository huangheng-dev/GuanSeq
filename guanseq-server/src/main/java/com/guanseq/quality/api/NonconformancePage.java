package com.guanseq.quality.api;

import java.util.List;

public record NonconformancePage(
		List<NonconformanceRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages,
		Summary summary,
		boolean canReview,
		boolean canExecuteAction,
		boolean canVerify) {

	public record Summary(long open, long reviewed, long actionRequired, long actionInProgress,
			long verificationPending, long closed, long overdue) { }
}
