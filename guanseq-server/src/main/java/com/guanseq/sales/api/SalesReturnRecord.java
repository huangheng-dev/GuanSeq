package com.guanseq.sales.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SalesReturnRecord(
		UUID id,
		String returnNumber,
		UUID salesOrderId,
		String orderNumber,
		UUID customerId,
		String customerCode,
		String customerName,
		LocalDate returnDate,
		String status,
		String reason,
		String note,
		UUID warehouseId,
		String warehouseCode,
		String warehouseName,
		UUID locationId,
		String locationCode,
		String locationName,
		BigDecimal totalReturnQuantity,
		Instant receivedAt,
		Instant inspectedAt,
		long version,
		Instant createdAt,
		Instant updatedAt,
		List<String> availableActions,
		List<Line> lines,
		List<Event> events) {

	public record Line(UUID id, UUID orderLineId, int lineNumber, UUID materialId, String materialCode,
			String materialName, String materialSpecification, String unit, BigDecimal authorizedQuantity,
			BigDecimal receivedQuantity, BigDecimal acceptedQuantity, BigDecimal rejectedQuantity, String lotNumber,
			UUID inspectionBalanceId, UUID receiptMovementId, String stockSummary) { }

	public record Event(UUID id, String action, String fromStatus, String toStatus, String reason, String requestId,
			Instant occurredAt) { }

	public record CreateRequest(@NotNull UUID salesOrderId, long expectedOrderVersion, @NotNull LocalDate returnDate,
			@NotBlank @Size(min = 4, max = 500) String reason, @Size(max = 500) String note,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines) { }

	public record LineInput(@NotNull UUID orderLineId,
			@NotNull @DecimalMin(value = "0.0001") BigDecimal returnQuantity) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "CANCEL|RECEIVE|INSPECT|REVERSE_RECEIPT") String action,
			long expectedVersion,
			@NotBlank @Size(min = 4, max = 500) String reason,
			UUID warehouseId,
			UUID locationId,
			@Size(max = 100) List<@Valid ActionLineInput> lines) { }

	public record ActionLineInput(@NotNull UUID returnLineId, @Size(max = 80) String lotNumber,
			@DecimalMin(value = "0.0000") BigDecimal acceptedQuantity,
			@DecimalMin(value = "0.0000") BigDecimal rejectedQuantity) { }
}
