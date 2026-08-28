package com.guanseq.identity.api;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OrganizationStructureCommand {
	private OrganizationStructureCommand() {}

	public record CreateSite(
			@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String code,
			@NotBlank @Size(max = 120) String name,
			UUID responsibleUserId) {}

	public record UpdateUnit(
			@NotBlank @Size(max = 120) String name,
			UUID responsibleUserId,
			@NotNull @Min(0) Long expectedVersion) {}

	public record UnitAction(
			@NotNull Action action,
			@NotNull @Min(0) Long expectedVersion,
			@NotBlank @Size(min = 4, max = 300) String reason) {}

	public record UpdateWorkspace(
			@NotBlank @Size(max = 120) String name,
			UUID responsibleUserId,
			@NotNull @Min(0) Long expectedVersion) {}

	public record AssignMember(
			@NotNull UUID organizationUnitId,
			@NotNull @Min(0) Long expectedMembershipVersion,
			@NotBlank @Size(min = 4, max = 300) String reason) {}

	public enum Action { ACTIVATE, DEACTIVATE }
}
