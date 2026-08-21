package com.guanseq.finance.api;

import java.util.List;

public record PayableInvoicePage(
		List<PayableInvoiceRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) { }
