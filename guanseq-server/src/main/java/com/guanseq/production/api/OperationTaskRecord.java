package com.guanseq.production.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OperationTaskRecord(
		UUID id,
		String taskNumber,
		UUID orderId,
		String orderNumber,
		UUID materialId,
		String materialCode,
		String materialName,
		String materialSpecification,
		String unit,
		BigDecimal plannedQuantity,
		String workshop,
		UUID routingId,
		String routingNumber,
		String routingVersionCode,
		UUID sourceOperationId,
		int sequenceNumber,
		String operationCode,
		String operationName,
		String workCenterCode,
		String workCenterName,
		BigDecimal setupMinutes,
		BigDecimal runMinutesPerUnit,
		BigDecimal queueMinutes,
		boolean inspectionRequired,
		String instructionSummary,
		String status,
		Instant startedAt,
		Instant completedAt,
		BigDecimal completedQuantity,
		String shiftName,
		String operatorName,
		String note,
		long version,
		Instant createdAt,
		Instant updatedAt,
		List<Event> events) {

	public record Event(UUID id, String action, String fromStatus, String toStatus, String requestId,
			String comment, String source, Instant occurredAt) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "START|COMPLETE") String action,
			long expectedVersion,
			@Size(max = 80) String shiftName,
			@Size(max = 80) String operatorName,
			@DecimalMin("0.000001") BigDecimal completedQuantity,
			@Size(max = 500) String note,
			@Pattern(regexp = "DESKTOP_FORM|MOBILE_SCAN") String source,
			@Size(max = 120) String operatorBadge) { }
}
