package com.guanseq.production.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OperationLaborEntryRecord(
		UUID id,
		String entryNumber,
		UUID taskId,
		String taskNumber,
		UUID orderId,
		String orderNumber,
		String operationCode,
		String operationName,
		String workCenterCode,
		String workCenterName,
		LocalDate workDate,
		String shiftName,
		String operatorName,
		BigDecimal actualMinutes,
		String status,
		String note,
		UUID approvedBy,
		Instant approvedAt,
		UUID voidedBy,
		Instant voidedAt,
		String voidReason,
		long version,
		Instant createdAt,
		Instant updatedAt,
		List<Event> events) {

	public record Event(UUID id, String action, String fromStatus, String toStatus, String requestId,
			String comment, Instant occurredAt) { }

	public record CreateRequest(
			@NotNull UUID taskId,
			@NotNull LocalDate workDate,
			@NotBlank @Size(max = 80) String shiftName,
			@NotBlank @Size(max = 80) String operatorName,
			@NotNull @DecimalMin("0.01") @DecimalMax("1440") BigDecimal actualMinutes,
			@Size(max = 500) String note) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "APPROVE|VOID") String action,
			long expectedVersion,
			@Size(max = 500) String reason) { }
}
