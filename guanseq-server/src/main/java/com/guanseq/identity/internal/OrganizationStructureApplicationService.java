package com.guanseq.identity.internal;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.identity.api.OrganizationStructureCommand;
import com.guanseq.identity.api.OrganizationStructurePage;
import com.guanseq.identity.api.OrganizationStructurePage.MemberRecord;
import com.guanseq.identity.api.OrganizationStructurePage.OrganizationUnitRecord;
import com.guanseq.identity.api.OrganizationStructurePage.WorkspaceRecord;
import com.guanseq.identity.api.WorkspacePermission;

@Service
public class OrganizationStructureApplicationService {
	private static final String ACTIVE = "ACTIVE";
	private static final String INACTIVE = "INACTIVE";

	private final CurrentWorkspaceProvider workspaceProvider;
	private final OrganizationUnitRepository organizationRepository;
	private final WorkspaceRepository workspaceRepository;
	private final IdentityUserRepository userRepository;
	private final WorkspaceMembershipRepository membershipRepository;
	private final AuditEventRepository auditEventRepository;

	OrganizationStructureApplicationService(CurrentWorkspaceProvider workspaceProvider,
			OrganizationUnitRepository organizationRepository, WorkspaceRepository workspaceRepository,
			IdentityUserRepository userRepository, WorkspaceMembershipRepository membershipRepository,
			AuditEventRepository auditEventRepository) {
		this.workspaceProvider = workspaceProvider;
		this.organizationRepository = organizationRepository;
		this.workspaceRepository = workspaceRepository;
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.auditEventRepository = auditEventRepository;
	}

	@Transactional(readOnly = true)
	public OrganizationStructurePage get(String username) {
		return page(requireAdministrator(username));
	}

	@Transactional
	public OrganizationStructurePage createSite(String username, OrganizationStructureCommand.CreateSite request) {
		CurrentWorkspaceAccess access = requireAdministrator(username);
		OrganizationUnitEntity operating = requireOperating(access);
		if (!"PLANT".equals(operating.getUnitType())) {
			throw unprocessable("只有工厂工作区可以建立直属现场单元");
		}
		UUID ownerId = requireActiveOwner(access, request.responsibleUserId());
		if (organizationRepository.findByCode(request.code().trim()).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "组织编码已经存在");
		}
		OrganizationUnitEntity site = new OrganizationUnitEntity(UUID.randomUUID(), request.code().trim(),
				request.name().trim(), "SITE", operating.getId());
		site.update(site.getName(), ownerId);
		try { organizationRepository.saveAndFlush(site); }
		catch (DataIntegrityViolationException exception) { throw new ResponseStatusException(HttpStatus.CONFLICT, "组织编码已经存在"); }
		audit(access, "ORGANIZATION_SITE_CREATED", "ORGANIZATION_UNIT", site.getId(),
				Map.of("code", site.getCode(), "name", site.getName()));
		return page(access);
	}

	@Transactional
	public OrganizationStructurePage updateUnit(String username, UUID unitId, OrganizationStructureCommand.UpdateUnit request) {
		CurrentWorkspaceAccess access = requireAdministrator(username);
		OrganizationUnitEntity unit = requireMutableUnit(access, unitId);
		if (unit.getVersion() != request.expectedVersion()) throw conflict();
		UUID ownerId = requireActiveOwner(access, request.responsibleUserId());
		String previousName = unit.getName();
		UUID previousOwner = unit.getResponsibleUserId();
		unit.update(request.name().trim(), ownerId);
		organizationRepository.saveAndFlush(unit);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("previousName", previousName); details.put("name", unit.getName());
		details.put("previousResponsibleUserId", string(previousOwner)); details.put("responsibleUserId", string(ownerId));
		audit(access, "ORGANIZATION_UNIT_UPDATED", "ORGANIZATION_UNIT", unit.getId(), details);
		return page(access);
	}

	@Transactional
	public OrganizationStructurePage actUnit(String username, UUID unitId, OrganizationStructureCommand.UnitAction request) {
		CurrentWorkspaceAccess access = requireAdministrator(username);
		OrganizationUnitEntity unit = requireSite(access, unitId);
		if (unit.getVersion() != request.expectedVersion()) throw conflict();
		String nextStatus = request.action() == OrganizationStructureCommand.Action.ACTIVATE ? ACTIVE : INACTIVE;
		if (nextStatus.equals(unit.getStatus())) return page(access);
		if (INACTIVE.equals(nextStatus)) {
			if (membershipRepository.countByWorkspaceIdAndOrganizationUnitIdAndStatus(access.workspaceId(), unit.getId(), ACTIVE) > 0) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "该现场单元仍有启用成员，不能停用");
			}
			if (workspaceRepository.countByOperatingOrganizationAndStatus(unit, ACTIVE) > 0) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "该现场单元仍承载启用工作区，不能停用");
			}
		}
		String previousStatus = unit.getStatus();
		unit.changeStatus(nextStatus);
		organizationRepository.saveAndFlush(unit);
		audit(access, ACTIVE.equals(nextStatus) ? "ORGANIZATION_SITE_ACTIVATED" : "ORGANIZATION_SITE_DEACTIVATED",
				"ORGANIZATION_UNIT", unit.getId(), Map.of("previousStatus", previousStatus, "status", nextStatus,
				"reason", request.reason().trim()));
		return page(access);
	}

	@Transactional
	public OrganizationStructurePage updateWorkspace(String username, OrganizationStructureCommand.UpdateWorkspace request) {
		CurrentWorkspaceAccess access = requireAdministrator(username);
		WorkspaceEntity workspace = requireWorkspace(access);
		if (workspace.getVersion() != request.expectedVersion()) throw conflict();
		UUID ownerId = requireActiveOwner(access, request.responsibleUserId());
		String previousName = workspace.getName();
		UUID previousOwner = workspace.getResponsibleUserId();
		workspace.update(request.name().trim(), ownerId);
		workspaceRepository.saveAndFlush(workspace);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("previousName", previousName); details.put("name", workspace.getName());
		details.put("previousResponsibleUserId", string(previousOwner)); details.put("responsibleUserId", string(ownerId));
		audit(access, "WORKSPACE_ORGANIZATION_UPDATED", "WORKSPACE", workspace.getId(), details);
		return page(access);
	}

	@Transactional
	public OrganizationStructurePage assignMember(String username, UUID userId, OrganizationStructureCommand.AssignMember request) {
		CurrentWorkspaceAccess access = requireAdministrator(username);
		WorkspaceMembershipEntity membership = membershipRepository.findByUserIdAndWorkspaceId(userId, access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前工作区成员不存在"));
		if (membership.getVersion() != request.expectedMembershipVersion()) throw conflict();
		OrganizationUnitEntity target = requireAssignableUnit(access, request.organizationUnitId());
		if (Objects.equals(membership.getOrganizationUnitId(), target.getId())) return page(access);
		UUID previousUnitId = membership.getOrganizationUnitId();
		membership.assignOrganization(target.getId());
		membershipRepository.saveAndFlush(membership);
		audit(access, "WORKSPACE_MEMBER_ORGANIZATION_ASSIGNED", "WORKSPACE_MEMBER", userId,
				Map.of("previousOrganizationUnitId", previousUnitId.toString(), "organizationUnitId", target.getId().toString(),
						"reason", request.reason().trim()));
		return page(access);
	}

	private OrganizationStructurePage page(CurrentWorkspaceAccess access) {
		WorkspaceEntity workspace = requireWorkspace(access);
		OrganizationUnitEntity company = organizationRepository.findById(access.tenantOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前租户公司不存在"));
		OrganizationUnitEntity operating = requireOperating(access);
		List<OrganizationUnitEntity> sites = "PLANT".equals(operating.getUnitType())
				? organizationRepository.findAllByParentIdOrderByName(operating.getId()).stream()
						.filter(unit -> "SITE".equals(unit.getUnitType())).toList()
				: List.of();
		List<WorkspaceMembershipEntity> memberships = membershipRepository.findAllByWorkspaceIdOrderByCreatedAt(access.workspaceId());
		Map<UUID, IdentityUserEntity> users = userRepository.findAllById(memberships.stream().map(WorkspaceMembershipEntity::getUserId).toList())
				.stream().collect(Collectors.toMap(IdentityUserEntity::getId, Function.identity()));
		Set<UUID> ownerIds = new HashSet<>();
		ownerIds.add(company.getResponsibleUserId()); ownerIds.add(operating.getResponsibleUserId()); ownerIds.add(workspace.getResponsibleUserId());
		sites.stream().map(OrganizationUnitEntity::getResponsibleUserId).forEach(ownerIds::add);
		ownerIds.remove(null);
		ownerIds.forEach(id -> userRepository.findByIdAndTenantOrganizationId(id, access.tenantOrganizationId()).ifPresent(user -> users.put(id, user)));
		Map<UUID, String> unitNames = java.util.stream.Stream.concat(java.util.stream.Stream.of(operating), sites.stream())
				.collect(Collectors.toMap(OrganizationUnitEntity::getId, OrganizationUnitEntity::getName));
		return new OrganizationStructurePage(access.userId(), unitRecord(company, users), unitRecord(operating, users),
				sites.stream().map(unit -> unitRecord(unit, users)).toList(), workspaceRecord(workspace, users),
				memberships.stream().map(membership -> memberRecord(membership, users, unitNames)).toList(),
				"当前工作区管理员仅可维护所在工厂、直属现场单元、工作区名称/负责人及成员归属；公司、其他工厂和其他工作区只在其自身权限域治理。");
	}

	private CurrentWorkspaceAccess requireAdministrator(String username) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		if (!WorkspacePermission.IDENTITY_ORGANIZATION_MANAGE.allows(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有当前工作区管理员可以维护组织与成员归属");
		}
		return access;
	}

	private WorkspaceEntity requireWorkspace(CurrentWorkspaceAccess access) {
		WorkspaceEntity workspace = workspaceRepository.findById(access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前工作区不存在"));
		if (!workspace.getTenantOrganization().getId().equals(access.tenantOrganizationId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前工作区不属于当前租户");
		}
		return workspace;
	}

	private OrganizationUnitEntity requireOperating(CurrentWorkspaceAccess access) {
		return organizationRepository.findById(access.operatingOrganizationId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前工作区所在组织不存在"));
	}

	private OrganizationUnitEntity requireMutableUnit(CurrentWorkspaceAccess access, UUID unitId) {
		if (access.tenantOrganizationId().equals(unitId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "公司根组织不在当前工作区维护范围");
		if (access.operatingOrganizationId().equals(unitId)) return requireOperating(access);
		return requireSite(access, unitId);
	}

	private OrganizationUnitEntity requireSite(CurrentWorkspaceAccess access, UUID unitId) {
		OrganizationUnitEntity unit = organizationRepository.findById(unitId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "现场单元不存在"));
		if (!"SITE".equals(unit.getUnitType()) || !access.operatingOrganizationId().equals(unit.getParentId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "现场单元不在当前工作区范围");
		}
		return unit;
	}

	private OrganizationUnitEntity requireAssignableUnit(CurrentWorkspaceAccess access, UUID unitId) {
		OrganizationUnitEntity unit = access.operatingOrganizationId().equals(unitId) ? requireOperating(access) : requireSite(access, unitId);
		if (!ACTIVE.equals(unit.getStatus())) throw unprocessable("成员只能归属到启用组织");
		return unit;
	}

	private UUID requireActiveOwner(CurrentWorkspaceAccess access, UUID userId) {
		if (userId == null) return null;
		IdentityUserEntity user = userRepository.findByIdAndTenantOrganizationId(userId, access.tenantOrganizationId())
				.orElseThrow(() -> unprocessable("负责人不是当前租户用户"));
		if (!ACTIVE.equals(user.getStatus()) || membershipRepository.findByUserIdAndWorkspaceIdAndStatus(userId, access.workspaceId(), ACTIVE).isEmpty()) {
			throw unprocessable("负责人必须是当前工作区启用成员");
		}
		return user.getId();
	}

	private static OrganizationUnitRecord unitRecord(OrganizationUnitEntity unit, Map<UUID, IdentityUserEntity> users) {
		IdentityUserEntity owner = users.get(unit.getResponsibleUserId());
		return new OrganizationUnitRecord(unit.getId(), unit.getCode(), unit.getName(), unit.getUnitType(), unit.getParentId(), unit.getStatus(),
				unit.getResponsibleUserId(), owner == null ? null : owner.getDisplayName(), unit.getVersion(), unit.getCreatedAt(), unit.getUpdatedAt());
	}

	private static WorkspaceRecord workspaceRecord(WorkspaceEntity workspace, Map<UUID, IdentityUserEntity> users) {
		IdentityUserEntity owner = users.get(workspace.getResponsibleUserId());
		return new WorkspaceRecord(workspace.getId(), workspace.getCode(), workspace.getName(), workspace.getStatus(),
				workspace.getOperatingOrganization().getId(), workspace.getResponsibleUserId(), owner == null ? null : owner.getDisplayName(),
				workspace.getVersion(), workspace.getCreatedAt(), workspace.getUpdatedAt());
	}

	private static MemberRecord memberRecord(WorkspaceMembershipEntity membership, Map<UUID, IdentityUserEntity> users, Map<UUID, String> unitNames) {
		IdentityUserEntity user = users.get(membership.getUserId());
		if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "成员用户不存在");
		return new MemberRecord(user.getId(), user.getUsername(), user.getDisplayName(), membership.getRoleCode(), membership.getStatus(),
				membership.getOrganizationUnitId(), unitNames.getOrDefault(membership.getOrganizationUnitId(), "未知组织"), membership.getVersion());
	}

	private void audit(CurrentWorkspaceAccess access, String eventType, String objectType, UUID objectId, Map<String, Object> details) {
		auditEventRepository.save(new AuditEventEntity(access.userId(), access.workspaceId(), eventType, objectType,
				objectId.toString(), MDC.get("requestId"), details));
	}

	private static String string(UUID value) { return value == null ? "" : value.toString(); }
	private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "组织事实已经变化，请刷新后重试"); }
	private static ResponseStatusException unprocessable(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
}
