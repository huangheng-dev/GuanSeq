package com.guanseq.identity.api;

import java.util.List;
import java.util.UUID;

public record WorkspaceUserPage(
		UUID currentUserId,
		UUID workspaceId,
		String workspaceCode,
		String workspaceName,
		String companyName,
		List<WorkspaceRoleRecord> availableRoles,
		List<WorkspaceUserRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages) {
}
