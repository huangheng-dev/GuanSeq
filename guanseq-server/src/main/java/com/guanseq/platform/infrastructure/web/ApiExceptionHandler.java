package com.guanseq.platform.infrastructure.web;

import java.util.List;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.platform.api.ApiErrorResponse;
import com.guanseq.platform.api.ApiErrorResponse.FieldViolation;

@RestControllerAdvice
class ApiExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(exception.getHeaders());
		String errorCode = headers.getFirst("X-Error-Code");
		String message = exception.getReason() == null || exception.getReason().isBlank()
				? "请求无法完成"
				: exception.getReason();
		return ResponseEntity.status(exception.getStatusCode())
				.headers(headers)
				.body(ApiErrorSupport.response(exception.getStatusCode(), message, errorCode));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		List<FieldViolation> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(),
						error.getDefaultMessage() == null ? "字段值无效" : error.getDefaultMessage()))
				.distinct()
				.toList();
		return ResponseEntity.badRequest().body(ApiErrorSupport.response(
				HttpStatus.BAD_REQUEST,
				"请求数据校验失败",
				"VALIDATION_FAILED",
				fieldErrors));
	}

	@ExceptionHandler({
			ConstraintViolationException.class,
			MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class
	})
	ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception) {
		return ResponseEntity.badRequest().body(ApiErrorSupport.response(
				HttpStatus.BAD_REQUEST,
				"请求格式或参数无效",
				"VALIDATION_FAILED"));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorSupport.response(
				HttpStatus.NOT_FOUND,
				"请求的资源不存在",
				"RESOURCE_NOT_FOUND"));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
		LOGGER.error("Unhandled API failure", exception);
		return ResponseEntity.internalServerError().body(ApiErrorSupport.response(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"系统暂时无法完成请求，请使用请求编号联系管理员",
				"INTERNAL_ERROR"));
	}
}
