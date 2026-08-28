package com.guanseq.identity.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.identity.api.WorkspaceAuditPage;
import com.guanseq.identity.api.WorkspaceAuditPage.ActorOption;
import com.guanseq.identity.api.WorkspaceAuditPage.AuditEventRecord;
import com.guanseq.identity.api.WorkspacePermission;

@Service
public class WorkspaceAuditApplicationService {
	private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);
	private static final Duration MAX_WINDOW = Duration.ofDays(90);
	private static final String SCOPE_DESCRIPTION =
			"仅展示当前工作区的身份、成员、角色、组织与工作区治理操作；销售、采购、生产、质量、库存和财务等业务证据仍归属各业务单据，不在此跨模块汇总。";

	private final CurrentWorkspaceProvider workspaceProvider;
	private final WorkspaceRepository workspaceRepository;
	private final AuditEventRepository auditEventRepository;
	private final IdentityUserRepository userRepository;

	WorkspaceAuditApplicationService(CurrentWorkspaceProvider workspaceProvider,
			WorkspaceRepository workspaceRepository, AuditEventRepository auditEventRepository,
			IdentityUserRepository userRepository) {
		this.workspaceProvider = workspaceProvider;
		this.workspaceRepository = workspaceRepository;
		this.auditEventRepository = auditEventRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public WorkspaceAuditPage list(String username, int page, int size, String eventType,
			String objectType, UUID actorId, String query, Instant occurredFrom, Instant occurredTo) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		if (!WorkspacePermission.IDENTITY_AUDIT_READ.allows(access.roleCode())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有当前工作区管理员可以查看系统操作审计");
		}
		WorkspaceEntity workspace = workspaceRepository.findById(access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前工作区不存在"));
		if (!workspace.getTenantOrganization().getId().equals(access.tenantOrganizationId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前工作区不属于当前租户");
		}

		Instant actualTo = occurredTo == null ? Instant.now() : occurredTo;
		Instant actualFrom = occurredFrom == null ? actualTo.minus(DEFAULT_WINDOW) : occurredFrom;
		if (actualFrom.isAfter(actualTo)) {
			throw unprocessable("开始时间不能晚于结束时间");
		}
		if (Duration.between(actualFrom, actualTo).compareTo(MAX_WINDOW) > 0) {
			throw unprocessable("单次审计查询时间范围不能超过 90 天");
		}

		Page<AuditEventEntity> result = auditEventRepository.findWorkspacePage(
				access.workspaceId(), actualFrom, actualTo, normalize(eventType), normalize(objectType), actorId,
				normalize(query), PageRequest.of(page, size,
						Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
		List<UUID> actorIds = auditEventRepository.findActorIds(access.workspaceId());
		Map<UUID, IdentityUserEntity> actors = userRepository.findAllById(actorIds).stream()
				.filter(user -> access.tenantOrganizationId().equals(user.getTenantOrganizationId()))
				.collect(Collectors.toMap(IdentityUserEntity::getId, Function.identity()));

		return new WorkspaceAuditPage(
				workspace.getId(), workspace.getCode(), workspace.getName(), workspace.getTenantOrganization().getName(),
				SCOPE_DESCRIPTION, actualFrom, actualTo,
				result.getContent().stream().map(event -> record(event, actors.get(event.getUserId()))).toList(),
				result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages(),
				auditEventRepository.findEventTypes(access.workspaceId()),
				auditEventRepository.findObjectTypes(access.workspaceId()),
				actors.values().stream().sorted(Comparator.comparing(IdentityUserEntity::getDisplayName)
						.thenComparing(IdentityUserEntity::getUsername))
						.map(user -> new ActorOption(user.getId(), user.getUsername(), user.getDisplayName())).toList());
	}

	private static AuditEventRecord record(AuditEventEntity event, IdentityUserEntity actor) {
		return new AuditEventRecord(event.getId(), event.getEventType(), event.getObjectType(), event.getObjectId(),
				event.getRequestId(), event.getUserId(), actor == null ? null : actor.getUsername(),
				actor == null ? null : actor.getDisplayName(), event.getDetails(), event.getOccurredAt());
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private static ResponseStatusException unprocessable(String reason) {
		return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
	}
}
