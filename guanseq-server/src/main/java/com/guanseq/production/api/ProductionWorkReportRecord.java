package com.guanseq.production.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductionWorkReportRecord(
		UUID id, String reportNumber, UUID orderId, String orderNumber, UUID materialId, String materialCode,
		String materialName, String materialSpecification, String unit, String workshop, String shiftName,
		String operatorName, BigDecimal reportedQuantity, String note, UUID inspectionId, String inspectionNumber,
		String inspectionStatus, String qualityResult, BigDecimal acceptedQuantity, BigDecimal rejectedQuantity,
		UUID receiptBalanceId, UUID receiptMovementId, String receiptWarehouse, String receiptLocation,
		String lotNumber, String status, long version, Instant createdAt, Instant settledAt) {

	public record CreateRequest(@NotNull UUID orderId, @NotNull @DecimalMin("0.0001") BigDecimal quantity,
			@NotBlank @Size(max = 80) String shiftName, @NotBlank @Size(max = 80) String operatorName,
			@Size(max = 500) String note, long expectedOrderVersion) { }

	public record SettleRequest(UUID warehouseId, UUID locationId, @Size(max = 80) String lotNumber,
			long expectedVersion) { }
}
