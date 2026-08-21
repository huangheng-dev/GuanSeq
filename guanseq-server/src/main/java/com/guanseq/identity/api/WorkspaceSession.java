package com.guanseq.identity.api;

import java.util.List;
import java.util.UUID;

public record WorkspaceSession(
		UUID userId,
		String username,
		String displayName,
		UUID currentWorkspaceId,
		long selectionVersion,
		List<WorkspaceSummary> workspaces) {

	public record WorkspaceSummary(
			UUID id,
			String code,
			String name,
			UUID organizationId,
			String companyName,
			String roleCode,
			boolean current) {
	}
}
