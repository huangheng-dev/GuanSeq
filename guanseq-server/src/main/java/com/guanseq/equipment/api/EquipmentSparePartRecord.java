package com.guanseq.equipment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EquipmentSparePartRecord(
		UUID id,
		UUID materialId,
		String materialCode,
		String materialName,
		String materialSpecification,
		String unit,
		UUID preferredWarehouseId,
		String preferredWarehouseCode,
		String preferredWarehouseName,
		BigDecimal reorderPoint,
		BigDecimal availableQuantity,
		BigDecimal standardUnitCost,
		String currency,
		LocalDate costEffectiveDate,
		String costStatus,
		String stockStatus,
		String status,
		long version,
		Instant updatedAt) {

	public record CreateRequest(
			@NotNull UUID materialId,
			@NotNull UUID preferredWarehouseId,
			@NotNull @PositiveOrZero BigDecimal reorderPoint,
			@NotBlank @Size(min = 4, max = 500) String reason) { }
}
