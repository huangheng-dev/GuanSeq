package com.guanseq.labeling.api;

import java.util.List;

public record LabelPrintRequestPage(List<LabelPrintRequestRecord> items, long total, int page, int size, int totalPages) { }

