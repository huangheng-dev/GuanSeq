package com.guanseq.planning.api;

import java.util.List;

public record MrpSuggestionPage(
		List<MrpSuggestionRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) { }
