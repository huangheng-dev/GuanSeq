package com.guanseq.procurement.api;

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

public record PurchaseReturnRecord(UUID id, String returnNumber, UUID purchaseOrderId, String orderNumber,
		UUID supplierId, String supplierCode, String supplierName, LocalDate returnDate, String status,
		String reason, String note, BigDecimal totalReturnQuantity, BigDecimal acceptedReturnQuantity,
		BigDecimal blockedReturnQuantity, long version, Instant createdAt, Instant updatedAt,
		List<String> availableActions, List<Line> lines, List<Event> events) {

	public record Line(UUID id, UUID purchaseReceiptLineId, UUID purchaseOrderLineId, int lineNumber,
			UUID materialId, String materialCode, String materialName, String materialSpecification, String unit,
			String qualityStatus, BigDecimal authorizedQuantity, BigDecimal shippedQuantity, UUID stockBalanceId,
			UUID stockMovementId, String warehouseCode, String locationCode, String lotNumber) { }

	public record Event(UUID id, String action, String fromStatus, String toStatus, String reason,
			String requestId, Instant occurredAt) { }

	public record CreateRequest(@NotNull UUID purchaseOrderId, long expectedOrderVersion, @NotNull LocalDate returnDate,
			@NotBlank @Size(min = 4, max = 500) String reason, @Size(max = 500) String note,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines) { }

	public record LineInput(@NotNull UUID purchaseReceiptLineId,
			@NotNull @Pattern(regexp = "AVAILABLE|BLOCKED") String qualityStatus,
			@NotNull @DecimalMin("0.0001") BigDecimal returnQuantity) { }

	public record ActionRequest(@NotNull @Pattern(regexp = "CANCEL|SHIP|REVERSE") String action,
			long expectedVersion, @NotBlank @Size(min = 4, max = 500) String reason) { }
}
