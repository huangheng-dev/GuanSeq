package com.guanseq.platform.infrastructure.web;

import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;

import com.guanseq.platform.api.ApiErrorResponse;
import com.guanseq.platform.api.ApiErrorResponse.FieldViolation;

final class ApiErrorSupport {

	private ApiErrorSupport() { }

	static ApiErrorResponse response(HttpStatusCode status, String message, String explicitCode) {
		return response(status, message, explicitCode, List.of());
	}

	static ApiErrorResponse response(
			HttpStatusCode status,
			String message,
			String explicitCode,
			List<FieldViolation> fieldErrors) {
		String requestId = MDC.get("requestId");
		return new ApiErrorResponse(
				explicitCode == null || explicitCode.isBlank() ? defaultCode(status.value()) : explicitCode,
				message,
				requestId == null || requestId.isBlank() ? "unavailable" : requestId,
				Instant.now(),
				List.copyOf(fieldErrors));
	}

	private static String defaultCode(int status) {
		return switch (status) {
			case 400 -> "VALIDATION_FAILED";
			case 401 -> "AUTHENTICATION_REQUIRED";
			case 403 -> "ACCESS_DENIED";
			case 404 -> "RESOURCE_NOT_FOUND";
			case 409 -> "BUSINESS_CONFLICT";
			case 422 -> "BUSINESS_RULE_VIOLATION";
			default -> status >= 500 ? "INTERNAL_ERROR" : "REQUEST_FAILED";
		};
	}
}
