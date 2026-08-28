package com.guanseq.production.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MaterialIssueRecord(
		UUID id,
		String issueNumber,
		UUID productionOrderId,
		String orderNumber,
		UUID materialId,
		String materialCode,
		String materialName,
		String materialSpecification,
		String unit,
		BigDecimal plannedQuantity,
		UUID warehouseId,
		String warehouseCode,
		String warehouseName,
		String status,
		String cancellationReason,
		long version,
		Instant createdAt,
		Instant updatedAt,
		List<Line> lines,
		List<MaterialReturnRecord> returns,
		List<Event> events,
		List<StockTransaction> stockTransactions) {

	public record Line(
			UUID id,
			int lineNumber,
			UUID componentMaterialId,
			String componentMaterialCode,
			String componentMaterialName,
			String componentMaterialSpecification,
			String unit,
			BigDecimal requiredQuantity,
			BigDecimal issuedQuantity,
			BigDecimal returnedQuantity,
			BigDecimal issuableQuantity,
			String bomNote,
			long version) { }

	public record Event(UUID id, String action, String fromStatus, String toStatus, String source, String requestId, Instant occurredAt) { }

	public record StockTransaction(
			UUID id,
			UUID issueLineId,
			UUID returnLineId,
			String movementType,
			String componentMaterialCode,
			BigDecimal quantity,
			UUID warehouseId,
			String warehouseCode,
			String warehouseName,
			UUID locationId,
			String locationCode,
			String locationName,
			UUID balanceId,
			String lotNumber,
			UUID movementId,
			String movementNumber,
			String source,
			String requestId,
			Instant occurredAt) { }

	public record CreateRequest(@NotNull UUID productionOrderId, @NotNull UUID warehouseId) { }

	public record IssueLineRequest(
			@NotNull UUID lineId,
			@NotNull @DecimalMin("0.000001") BigDecimal quantity,
			long expectedLineVersion,
			UUID stockBalanceId,
			Long expectedStockVersion) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "ISSUE|CANCEL") String action,
			long expectedVersion,
			@Size(max = 500) String comment,
			@Size(max = 200) List<@Valid IssueLineRequest> lines,
			@Pattern(regexp = "DESKTOP_FORM|MOBILE_SCAN") String source) { }

	public record ReturnLineRequest(
			@NotNull UUID lineId,
			@NotNull @DecimalMin("0.000001") BigDecimal quantity,
			long expectedLineVersion,
			@Size(max = 500) String reason) { }

	public record ReturnRequest(
			@NotNull UUID locationId,
			@NotBlank @Size(max = 500) String reason,
			@NotEmpty @Size(max = 200) List<@Valid ReturnLineRequest> lines) { }
}

