package com.guanseq.identity.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.WorkspaceSession;
import com.guanseq.identity.api.WorkspaceSession.WorkspaceSummary;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class WorkspaceApplicationService implements CurrentWorkspaceProvider {

	private static final String ACTIVE = "ACTIVE";

	private final IdentityUserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceMembershipRepository membershipRepository;
	private final UserWorkspacePreferenceRepository preferenceRepository;
	private final AuditEventRepository auditEventRepository;

	WorkspaceApplicationService(
			IdentityUserRepository userRepository,
			WorkspaceRepository workspaceRepository,
			WorkspaceMembershipRepository membershipRepository,
			UserWorkspacePreferenceRepository preferenceRepository,
			AuditEventRepository auditEventRepository) {
		this.userRepository = userRepository;
		this.workspaceRepository = workspaceRepository;
		this.membershipRepository = membershipRepository;
		this.preferenceRepository = preferenceRepository;
		this.auditEventRepository = auditEventRepository;
	}

	@Transactional(readOnly = true)
	public WorkspaceSession getSession(String username) {
		IdentityUserEntity user = requireUser(username);
		return buildSession(user);
	}

	@Override
	@Transactional(readOnly = true)
	public CurrentWorkspaceAccess resolve(String username) {
		IdentityUserEntity user = requireUser(username);
		WorkspaceSession session = buildSession(user);
		WorkspaceEntity workspace = workspaceRepository.findById(session.currentWorkspaceId())
				.filter(candidate -> ACTIVE.equals(candidate.getStatus()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前工作区不可用"));
		String roleCode = membershipRepository.findRoleCode(user.getId(), workspace.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前工作区成员关系无效"));
		return new CurrentWorkspaceAccess(
				user.getId(),
				user.getUsername(),
				workspace.getId(),
				workspace.getTenantOrganization().getId(),
				workspace.getOperatingOrganization().getId(),
				roleCode);
	}

	@Transactional
	public WorkspaceSession switchWorkspace(String username, UUID workspaceId, long expectedVersion) {
		IdentityUserEntity user = requireUser(username);
		membershipRepository.findByUserIdAndWorkspaceIdAndStatus(user.getId(), workspaceId, ACTIVE)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.FORBIDDEN,
						"当前用户无权访问该工作区"));

		WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
				.filter(candidate -> ACTIVE.equals(candidate.getStatus()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工作区不存在或已停用"));
		UserWorkspacePreferenceEntity preference = preferenceRepository.findById(user.getId())
				.orElseGet(() -> new UserWorkspacePreferenceEntity(user.getId(), workspaceId));

		if (preference.getVersion() != expectedVersion) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "工作区状态已经变化，请刷新后重试");
		}
		if (workspaceId.equals(preference.getCurrentWorkspaceId())) {
			return buildSession(user);
		}

		UUID previousWorkspaceId = preference.getCurrentWorkspaceId();
		preference.select(workspaceId);
		preferenceRepository.saveAndFlush(preference);

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("previousWorkspaceId", previousWorkspaceId.toString());
		details.put("currentWorkspaceId", workspaceId.toString());
		auditEventRepository.save(new AuditEventEntity(
				user.getId(),
				workspaceId,
				"WORKSPACE_SWITCHED",
				"WORKSPACE",
				workspace.getCode(),
				MDC.get("requestId"),
				details));

		return buildSession(user);
	}

	private IdentityUserEntity requireUser(String username) {
		return userRepository.findByUsernameAndStatus(username, ACTIVE)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在或已停用"));
	}

	private WorkspaceSession buildSession(IdentityUserEntity user) {
		List<WorkspaceEntity> accessibleWorkspaces = workspaceRepository.findAccessibleByUserId(user.getId());
		if (accessibleWorkspaces.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户没有可访问的工作区");
		}

		UserWorkspacePreferenceEntity preference = preferenceRepository.findById(user.getId()).orElse(null);
		UUID preferredWorkspaceId = preference == null ? null : preference.getCurrentWorkspaceId();
		UUID currentWorkspaceId = accessibleWorkspaces.stream()
				.map(WorkspaceEntity::getId)
				.filter(id -> id.equals(preferredWorkspaceId))
				.findFirst()
				.orElse(accessibleWorkspaces.getFirst().getId());
		long version = preference == null ? 0 : preference.getVersion();
		List<WorkspaceSummary> workspaces = accessibleWorkspaces.stream()
				.map(workspace -> new WorkspaceSummary(
						workspace.getId(),
						workspace.getCode(),
						workspace.getName(),
						workspace.getOperatingOrganization().getId(),
						workspace.getTenantOrganization().getName(),
						membershipRepository.findRoleCode(user.getId(), workspace.getId()).orElse("MEMBER"),
						workspace.getId().equals(currentWorkspaceId)))
				.toList();

		return new WorkspaceSession(
				user.getId(),
				user.getUsername(),
				user.getDisplayName(),
				currentWorkspaceId,
				version,
				workspaces);
	}
}
