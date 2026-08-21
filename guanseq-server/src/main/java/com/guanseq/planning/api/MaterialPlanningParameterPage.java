package com.guanseq.planning.api;

import java.util.List;

public record MaterialPlanningParameterPage(List<MaterialPlanningParameterRecord> items, long totalElements,
		int page, int size, int totalPages) { }
