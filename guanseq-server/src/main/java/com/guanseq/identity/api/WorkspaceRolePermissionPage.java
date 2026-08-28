package com.guanseq.identity.api;

import java.util.List;
import java.util.UUID;

public record WorkspaceRolePermissionPage(
		UUID workspaceId,
		String workspaceCode,
		String workspaceName,
		String companyName,
		String catalogVersion,
		String scopeDescription,
		List<WorkspaceRoleRecord> roles,
		List<PermissionGroup> groups) {

	public record PermissionGroup(String moduleCode, String moduleName, List<Permission> permissions) {
	}

	public record Permission(
			String code,
			String name,
			String description,
			String risk,
			List<String> roleCodes) {
	}
}
