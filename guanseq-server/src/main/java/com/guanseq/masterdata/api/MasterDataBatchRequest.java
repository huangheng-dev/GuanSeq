package com.guanseq.masterdata.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MasterDataBatchRequest(
		@NotEmpty List<@Valid Target> records,
		@Pattern(regexp = "ACTIVE|INACTIVE") String status,
		@Size(max = 80) String owner) {

	public record Target(@NotNull UUID id, long expectedVersion) {
	}
}
