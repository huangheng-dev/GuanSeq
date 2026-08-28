package com.guanseq.identity.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.identity.api.WorkspacePermission;
import com.guanseq.identity.api.WorkspaceRoleCatalog;
import com.guanseq.identity.api.WorkspaceRolePermissionPage;

@Service
public class WorkspaceRolePermissionApplicationService {

	private static final String CATALOG_VERSION = "2026-08-28.3";
	private static final String SCOPE_DESCRIPTION = "只展示后端显式角色门禁；租户、工作区、对象状态、本人范围、并发版本和字段规则仍由具体用例继续校验。";

	private final CurrentWorkspaceProvider workspaceProvider;
	private final WorkspaceRepository workspaceRepository;

	WorkspaceRolePermissionApplicationService(
			CurrentWorkspaceProvider workspaceProvider,
			WorkspaceRepository workspaceRepository) {
		this.workspaceProvider = workspaceProvider;
		this.workspaceRepository = workspaceRepository;
	}

	@Transactional(readOnly = true)
	public WorkspaceRolePermissionPage list(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		if (!WorkspacePermission.IDENTITY_ROLE_MATRIX_READ.allows(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有当前工作区管理员可以查看角色权限矩阵");
		}
		WorkspaceEntity workspace = workspaceRepository.findById(access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前工作区不存在"));
		return new WorkspaceRolePermissionPage(
				workspace.getId(),
				workspace.getCode(),
				workspace.getName(),
				workspace.getTenantOrganization().getName(),
				CATALOG_VERSION,
				SCOPE_DESCRIPTION,
				WorkspaceRoleCatalog.roles(),
				groups());
	}

	private static List<WorkspaceRolePermissionPage.PermissionGroup> groups() {
		Map<String, List<WorkspacePermission>> byModule = new LinkedHashMap<>();
		for (WorkspacePermission permission : WorkspacePermission.catalog()) {
			byModule.computeIfAbsent(permission.moduleCode(), ignored -> new java.util.ArrayList<>()).add(permission);
		}
		return byModule.values().stream().map(permissions -> {
			WorkspacePermission first = permissions.getFirst();
			return new WorkspaceRolePermissionPage.PermissionGroup(
					first.moduleCode(),
					first.moduleName(),
					permissions.stream().map(permission -> new WorkspaceRolePermissionPage.Permission(
							permission.name(),
							permission.displayName(),
							permission.description(),
							permission.risk().name(),
							permission.roleCodes())).toList());
		}).toList();
	}
}
