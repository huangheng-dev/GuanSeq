package com.guanseq.finance.api;

import java.util.List;

public record PayableCreditNotePage(
		List<PayableCreditNoteRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) { }
