package com.guanseq.finance.api;

import java.util.List;

public record ReceivableCreditNotePage(
		List<ReceivableCreditNoteRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) { }
