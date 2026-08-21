package com.guanseq.finance.api;

import java.util.List;

public record ReceivableInvoicePage(
		List<ReceivableInvoiceRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) { }
