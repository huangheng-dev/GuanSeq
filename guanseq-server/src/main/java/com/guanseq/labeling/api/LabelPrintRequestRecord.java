package com.guanseq.labeling.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LabelPrintRequestRecord(
		UUID id,
		String requestNumber,
		String objectType,
		UUID objectId,
		long objectVersion,
		String objectCode,
		String objectName,
		String objectDetail,
		String payload,
		String templateCode,
		String templateVersion,
		String mode,
		int copies,
		String reason,
		String status,
		String actorUsername,
		String requestId,
		Instant preparedAt) {

	public record PrepareRequest(
			@NotNull @Pattern(regexp = "OPERATION_TASK|EMPLOYEE|STOCK_BALANCE") String objectType,
			@NotNull UUID objectId,
			@Min(0) long expectedObjectVersion,
			@NotNull @Pattern(regexp = "INITIAL|REPRINT") String mode,
			@Min(1) @Max(10) int copies,
			@Size(max = 300) String reason) { }
}

