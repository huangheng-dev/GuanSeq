package com.guanseq.identity.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class InitialWorkspaceBootstrap {

	private InitialWorkspaceBootstrap() {
	}

	public record Request(
			@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String tenantCode,
			@NotBlank @Size(max = 120) String tenantName,
			@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String plantCode,
			@NotBlank @Size(max = 120) String plantName,
			@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String workspaceCode,
			@NotBlank @Size(max = 120) String workspaceName,
			@NotBlank @Size(max = 80) @Pattern(regexp = "\\S+") String externalUsername,
			@NotBlank @Size(max = 80) String displayName) {
	}

	public record Response(
			String status,
			UUID tenantOrganizationId,
			UUID operatingOrganizationId,
			UUID workspaceId,
			UUID userId,
			String username) {
	}
}
