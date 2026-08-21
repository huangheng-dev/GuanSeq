package com.guanseq.procurement.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PurchaseOrderRecord(
		UUID id, String orderNumber, UUID supplierId, String supplierCode, String supplierName,
		String currency, BigDecimal taxRate, LocalDate requestedReceiptDate, LocalDate promisedReceiptDate,
		String buyer, String status, BigDecimal totalNetAmount, BigDecimal totalTaxAmount,
		BigDecimal totalGrossAmount, String rejectionReason, String sourceType, UUID sourceId, String sourceNumber,
		long version, Instant updatedAt, List<Line> lines) {

	public record Line(UUID id, int lineNumber, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, BigDecimal orderedQuantity, BigDecimal receivedQuantity,
			BigDecimal outstandingQuantity, BigDecimal unitPrice, BigDecimal netAmount, BigDecimal taxAmount,
			BigDecimal grossAmount) { }

	public record LineInput(@NotNull UUID materialId,
			@NotNull @DecimalMin("0.0001") BigDecimal orderedQuantity,
			@NotNull @DecimalMin("0.0000") BigDecimal unitPrice) { }

	public record CreateRequest(@NotNull UUID supplierId,
			@NotNull @Pattern(regexp = "CNY|USD|EUR") String currency,
			@NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal taxRate,
			@NotNull LocalDate requestedReceiptDate, LocalDate promisedReceiptDate,
			@NotBlank @Size(max = 80) String buyer,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines) { }

	public record UpdateRequest(@NotNull UUID supplierId,
			@NotNull @Pattern(regexp = "CNY|USD|EUR") String currency,
			@NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal taxRate,
			@NotNull LocalDate requestedReceiptDate, LocalDate promisedReceiptDate,
			@NotBlank @Size(max = 80) String buyer,
			@NotEmpty @Size(max = 100) List<@Valid LineInput> lines, long expectedVersion) { }

	public record ActionRequest(
			@NotNull @Pattern(regexp = "SUBMIT|APPROVE|REJECT|RELEASE") String action,
			long expectedVersion, @Size(max = 500) String comment) { }
}
