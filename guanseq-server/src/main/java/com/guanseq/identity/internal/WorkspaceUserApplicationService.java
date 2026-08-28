package com.guanseq.identity.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.identity.api.WorkspaceRoleCatalog;
import com.guanseq.identity.api.WorkspaceUserPage;
import com.guanseq.identity.api.WorkspaceUserRecord;

@Service
public class WorkspaceUserApplicationService {

	private static final String ACTIVE = "ACTIVE";
	private static final String INACTIVE = "INACTIVE";
	private static final Set<String> ROLE_CODES = WorkspaceRoleCatalog.codes();

	private final CurrentWorkspaceProvider workspaceProvider;
	private final IdentityUserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceMembershipRepository membershipRepository;
	private final AuditEventRepository auditEventRepository;

	WorkspaceUserApplicationService(
			CurrentWorkspaceProvider workspaceProvider,
			IdentityUserRepository userRepository,
			WorkspaceRepository workspaceRepository,
			WorkspaceMembershipRepository membershipRepository,
			AuditEventRepository auditEventRepository) {
		this.workspaceProvider = workspaceProvider;
		this.userRepository = userRepository;
		this.workspaceRepository = workspaceRepository;
		this.membershipRepository = membershipRepository;
		this.auditEventRepository = auditEventRepository;
	}

	@Transactional(readOnly = true)
	public WorkspaceUserPage list(String username, String query, String status, int page, int size) {
		CurrentWorkspaceAccess access = requireAdministrator(username);
		String normalizedStatus = normalizeStatus(status);
		int actualPage = Math.max(0, page);
		int actualSize = Math.min(100, Math.max(1, size));
		Page<WorkspaceMembershipEntity> memberships = membershipRepository.findWorkspacePage(
				access.workspaceId(),
				query == null ? "" : query.trim(),
				normalizedStatus,
				PageRequest.of(actualPage, actualSize));
		Map<UUID, IdentityUserEntity> users = userRepository.findAllById(
				memberships.getContent().stream().map(WorkspaceMembershipEntity::getUserId).toList()).stream()
				.collect(Collectors.toMap(IdentityUserEntity::getId, Function.identity()));
		WorkspaceEntity workspace = workspaceRepository.findById(access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前工作区不存在"));
		List<WorkspaceUserRecord> items = memberships.getContent().stream()
				.map(membership -> toRecord(requireTenantUser(users.get(membership.getUserId()), access), membership))
				.toList();
		return new WorkspaceUserPage(
				access.userId(),
				workspace.getId(),
				workspace.getCode(),
				workspace.getName(),
				workspace.getTenantOrganization().getName(),
				WorkspaceRoleCatalog.roles(),
				items,
				memberships.getTotalElements(),
				memberships.getNumber(),
				memberships.getSize(),
				memberships.getTotalPages());
	}

	@Transactional
	public WorkspaceUserRecord create(String username, WorkspaceUserRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = requireAdministrator(username);
		requireRoleCode(request.roleCode());
		String normalizedUsername = request.username().trim();
		if (userRepository.findByUsername(normalizedUsername).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "该外部用户名不能用于新账号，请核对后重试");
		}
		IdentityUserEntity user = new IdentityUserEntity(
				UUID.randomUUID(),
				access.tenantOrganizationId(),
				normalizedUsername,
				request.displayName().trim());
		WorkspaceMembershipEntity membership = new WorkspaceMembershipEntity(
				UUID.randomUUID(), user.getId(), access.workspaceId(), request.roleCode());
		try {
			userRepository.saveAndFlush(user);
			membershipRepository.saveAndFlush(membership);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "用户已经存在或成员关系发生冲突");
		}

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("targetUserId", user.getId().toString());
		details.put("username", user.getUsername());
		details.put("roleCode", membership.getRoleCode());
		details.put("membershipStatus", membership.getStatus());
		audit(access, "USER_PROVISIONED", user.getId(), details);
		return toRecord(user, membership);
	}

	@Transactional
	public WorkspaceUserRecord update(String username, UUID userId, WorkspaceUserRecord.UpdateRequest request) {
		CurrentWorkspaceAccess access = requireAdministrator(username);
		requireDifferentUser(access, userId);
		requireRoleCode(request.roleCode());
		IdentityUserEntity user = requireUser(access, userId);
		WorkspaceMembershipEntity membership = requireMembership(access, userId);
		if (user.getVersion() != request.expectedUserVersion()
				|| membership.getVersion() != request.expectedMembershipVersion()) {
			throw conflict();
		}

		String previousDisplayName = user.getDisplayName();
		String previousRoleCode = membership.getRoleCode();
		user.updateDisplayName(request.displayName().trim());
		membership.updateRole(request.roleCode());
		userRepository.saveAndFlush(user);
		membershipRepository.saveAndFlush(membership);

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("previousDisplayName", previousDisplayName);
		details.put("displayName", user.getDisplayName());
		details.put("previousRoleCode", previousRoleCode);
		details.put("roleCode", membership.getRoleCode());
		audit(access, "WORKSPACE_MEMBER_UPDATED", user.getId(), details);
		return toRecord(user, membership);
	}

	@Transactional
	public WorkspaceUserRecord act(String username, UUID userId, WorkspaceUserRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = requireAdministrator(username);
		requireDifferentUser(access, userId);
		IdentityUserEntity user = requireUser(access, userId);
		WorkspaceMembershipEntity membership = requireMembership(access, userId);
		if (membership.getVersion() != request.expectedMembershipVersion()) throw conflict();
		String nextStatus = request.action() == WorkspaceUserRecord.Action.ACTIVATE ? ACTIVE : INACTIVE;
		if (nextStatus.equals(membership.getStatus())) return toRecord(user, membership);
		if (ACTIVE.equals(nextStatus) && !ACTIVE.equals(user.getStatus())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "内部账号未启用，不能恢复工作区访问");
		}
		if (INACTIVE.equals(nextStatus)
				&& "ADMIN".equals(membership.getRoleCode())
				&& membershipRepository.countByWorkspaceIdAndRoleCodeAndStatus(access.workspaceId(), "ADMIN", ACTIVE) <= 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "当前工作区必须保留至少一名启用管理员");
		}

		String previousStatus = membership.getStatus();
		membership.changeStatus(nextStatus);
		membershipRepository.saveAndFlush(membership);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("previousStatus", previousStatus);
		details.put("status", nextStatus);
		details.put("roleCode", membership.getRoleCode());
		details.put("reason", request.reason().trim());
		audit(access, ACTIVE.equals(nextStatus) ? "WORKSPACE_MEMBER_ACTIVATED" : "WORKSPACE_MEMBER_DEACTIVATED", user.getId(), details);
		return toRecord(user, membership);
	}

	private CurrentWorkspaceAccess requireAdministrator(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		if (!WorkspaceRoleCatalog.ADMIN.equals(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有当前工作区管理员可以维护成员与角色");
		}
		return access;
	}

	private static String normalizeStatus(String status) {
		String normalized = status == null ? "ALL" : status.trim().toUpperCase();
		if (!Set.of("ALL", ACTIVE, INACTIVE).contains(normalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "成员状态筛选无效");
		}
		return normalized;
	}

	private static void requireRoleCode(String roleCode) {
		if (!ROLE_CODES.contains(roleCode)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色编码不在当前受控目录中");
		}
	}

	private static void requireDifferentUser(CurrentWorkspaceAccess access, UUID userId) {
		if (access.userId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "不能在当前用例中修改自己的成员权限");
		}
	}

	private IdentityUserEntity requireUser(CurrentWorkspaceAccess access, UUID userId) {
		return userRepository.findByIdAndTenantOrganizationId(userId, access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在或不属于当前租户"));
	}

	private static IdentityUserEntity requireTenantUser(IdentityUserEntity user, CurrentWorkspaceAccess access) {
		if (user == null || !access.tenantOrganizationId().equals(user.getTenantOrganizationId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "成员用户不存在或不属于当前租户");
		}
		return user;
	}

	private WorkspaceMembershipEntity requireMembership(CurrentWorkspaceAccess access, UUID userId) {
		return membershipRepository.findByUserIdAndWorkspaceId(userId, access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前工作区成员不存在"));
	}

	private void audit(CurrentWorkspaceAccess access, String eventType, UUID targetUserId, Map<String, Object> details) {
		auditEventRepository.save(new AuditEventEntity(
				access.userId(),
				access.workspaceId(),
				eventType,
				"WORKSPACE_MEMBER",
				targetUserId.toString(),
				MDC.get("requestId"),
				details));
	}

	private static WorkspaceUserRecord toRecord(IdentityUserEntity user, WorkspaceMembershipEntity membership) {
		return new WorkspaceUserRecord(
				user.getId(),
				user.getUsername(),
				user.getDisplayName(),
				user.getStatus(),
				membership.getId(),
				membership.getStatus(),
				membership.getRoleCode(),
				user.getVersion(),
				membership.getVersion(),
				membership.getCreatedAt(),
				membership.getUpdatedAt());
	}

	private static ResponseStatusException conflict() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "用户或成员关系已经变化，请刷新后重试");
	}
}
