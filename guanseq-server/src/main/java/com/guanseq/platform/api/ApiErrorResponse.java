package com.guanseq.platform.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
		String code,
		String message,
		String requestId,
		Instant timestamp,
		List<FieldViolation> fieldErrors) {

	public record FieldViolation(String field, String message) { }
}
