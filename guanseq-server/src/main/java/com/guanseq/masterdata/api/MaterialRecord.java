package com.guanseq.masterdata.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MaterialRecord(
		UUID id,
		String code,
		String name,
		String specification,
		String materialType,
		String baseUnit,
		String procurementType,
		boolean incomingInspectionRequired,
		String owner,
		String status,
		long version,
		Instant updatedAt) {

	public record CreateRequest(
			@NotBlank @Size(max = 60) String code,
			@NotBlank @Size(max = 160) String name,
			@Size(max = 240) String specification,
			@NotNull @Pattern(regexp = "FINISHED_GOOD|SEMI_FINISHED|RAW_MATERIAL|PACKAGING|CONSUMABLE") String materialType,
			@NotBlank @Size(max = 20) String baseUnit,
			@NotNull @Pattern(regexp = "MAKE|BUY|OUTSOURCE") String procurementType,
			@NotBlank @Size(max = 80) String owner) {
	}

	public record UpdateRequest(
			@NotBlank @Size(max = 60) String code,
			@NotBlank @Size(max = 160) String name,
			@Size(max = 240) String specification,
			@NotNull @Pattern(regexp = "FINISHED_GOOD|SEMI_FINISHED|RAW_MATERIAL|PACKAGING|CONSUMABLE") String materialType,
			@NotBlank @Size(max = 20) String baseUnit,
			@NotNull @Pattern(regexp = "MAKE|BUY|OUTSOURCE") String procurementType,
			@NotBlank @Size(max = 80) String owner,
			long expectedVersion) {
	}
}
