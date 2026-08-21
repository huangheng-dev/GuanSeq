package com.guanseq.quality.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FinalInspectionRecord(
		UUID id, String inspectionNumber, String sourceType, UUID sourceId, String sourceNumber,
		UUID orderId, String orderNumber, UUID materialId, String materialCode, String materialName,
		String materialSpecification, String unit, BigDecimal inspectionQuantity, String status, String result,
		BigDecimal acceptedQuantity, BigDecimal rejectedQuantity, String inspector, String defectDescription,
		String conclusion, long version, Instant createdAt, Instant completedAt) {

	public record CompleteRequest(
			@NotNull @DecimalMin("0") BigDecimal acceptedQuantity,
			@NotNull @DecimalMin("0") BigDecimal rejectedQuantity,
			@NotBlank @Size(max = 80) String inspector,
			@Size(max = 500) String defectDescription,
			@NotBlank @Size(max = 500) String conclusion,
			long expectedVersion) { }
}
