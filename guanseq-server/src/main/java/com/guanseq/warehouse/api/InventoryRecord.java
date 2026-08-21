package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InventoryRecord(
		UUID id,
		UUID warehouseId,
		String warehouseCode,
		String warehouseName,
		UUID locationId,
		String locationCode,
		String locationName,
		UUID materialId,
		String materialCode,
		String materialName,
		String materialSpecification,
		String unit,
		String lotNumber,
		String qualityStatus,
		BigDecimal onHandQuantity,
		BigDecimal allocatedQuantity,
		BigDecimal frozenQuantity,
		BigDecimal availableQuantity,
		long version,
		Instant updatedAt,
		List<Movement> movements) {

	public record Movement(
			UUID id,
			String movementNumber,
			String movementType,
			BigDecimal quantity,
			String reason,
			String requestId,
			BigDecimal beforeOnHand,
			BigDecimal afterOnHand,
			BigDecimal beforeAllocated,
			BigDecimal afterAllocated,
			BigDecimal beforeFrozen,
			BigDecimal afterFrozen,
			Instant occurredAt) {
	}

	public record MovementRequest(
			@NotNull @Pattern(regexp = "RECEIPT|ISSUE|ALLOCATE|DEALLOCATE|FREEZE|UNFREEZE") String movementType,
			@NotNull @Positive BigDecimal quantity,
			@NotBlank @Size(max = 500) String reason,
			long expectedVersion) {
	}
}
