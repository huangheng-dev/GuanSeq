package com.guanseq.procurement.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierRecord(
		UUID id,
		String code,
		String name,
		String contactName,
		String contactPhone,
		String status,
		long version,
		Instant createdAt,
		Instant updatedAt) {

	public record CreateRequest(
			@NotBlank @Size(max = 40) String code,
			@NotBlank @Size(max = 160) String name,
			@Size(max = 80) String contactName,
			@Size(max = 40) String contactPhone) { }

	public record UpdateRequest(
			@NotBlank @Size(max = 160) String name,
			@Size(max = 80) String contactName,
			@Size(max = 40) String contactPhone,
			long expectedVersion) { }

	public record StatusRequest(
			@Pattern(regexp = "ACTIVE|INACTIVE") String status,
			long expectedVersion) { }
}
