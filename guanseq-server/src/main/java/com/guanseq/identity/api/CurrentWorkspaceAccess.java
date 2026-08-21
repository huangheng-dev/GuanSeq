package com.guanseq.identity.api;

import java.util.UUID;

public record CurrentWorkspaceAccess(
		UUID userId,
		String username,
		UUID workspaceId,
		UUID tenantOrganizationId,
		UUID operatingOrganizationId,
		String roleCode) {
}
