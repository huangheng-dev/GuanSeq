package com.guanseq.identity.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkspaceUserRecord(
		UUID userId,
		String username,
		String displayName,
		String accountStatus,
		UUID membershipId,
		String membershipStatus,
		String roleCode,
		long userVersion,
		long membershipVersion,
		Instant createdAt,
		Instant updatedAt) {

	public record CreateRequest(
			@NotBlank @Size(max = 80) @Pattern(regexp = "\\S+") String username,
			@NotBlank @Size(max = 80) String displayName,
			@NotBlank String roleCode) {
	}

	public record UpdateRequest(
			@NotBlank @Size(max = 80) String displayName,
			@NotBlank String roleCode,
			@NotNull @Min(0) Long expectedUserVersion,
			@NotNull @Min(0) Long expectedMembershipVersion) {
	}

	public record ActionRequest(
			@NotNull Action action,
			@NotNull @Min(0) Long expectedMembershipVersion,
			@NotBlank @Size(min = 4, max = 300) String reason) {
	}

	public enum Action {
		ACTIVATE,
		DEACTIVATE
	}
}
