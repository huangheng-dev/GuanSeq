package com.guanseq.masterdata.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRecord(
		UUID id,
		String code,
		String name,
		String customerType,
		String creditLevel,
		String contactName,
		String contactPhone,
		String owner,
		String status,
		long version,
		Instant updatedAt) {

	public record CreateRequest(
			@NotBlank @Size(max = 40) String code,
			@NotBlank @Size(max = 160) String name,
			@NotNull @Pattern(regexp = "ENTERPRISE|DISTRIBUTOR|INTERNAL") String customerType,
			@NotNull @Pattern(regexp = "A|B|C") String creditLevel,
			@Size(max = 80) String contactName,
			@Size(max = 40) String contactPhone,
			@NotBlank @Size(max = 80) String owner) {
	}

	public record UpdateRequest(
			@NotBlank @Size(max = 40) String code,
			@NotBlank @Size(max = 160) String name,
			@NotNull @Pattern(regexp = "ENTERPRISE|DISTRIBUTOR|INTERNAL") String customerType,
			@NotNull @Pattern(regexp = "A|B|C") String creditLevel,
			@Size(max = 80) String contactName,
			@Size(max = 40) String contactPhone,
			@NotBlank @Size(max = 80) String owner,
			long expectedVersion) {
	}
}
