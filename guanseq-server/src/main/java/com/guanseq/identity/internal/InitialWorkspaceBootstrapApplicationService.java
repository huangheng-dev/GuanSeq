package com.guanseq.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.identity.api.InitialWorkspaceBootstrap.Request;
import com.guanseq.identity.api.InitialWorkspaceBootstrap.Response;

@Service
@ConditionalOnProperty(name = "guanseq.bootstrap.enabled", havingValue = "true")
public class InitialWorkspaceBootstrapApplicationService {

	private final OrganizationUnitRepository organizationRepository;
	private final WorkspaceRepository workspaceRepository;
	private final IdentityUserRepository userRepository;
	private final WorkspaceMembershipRepository membershipRepository;
	private final UserWorkspacePreferenceRepository preferenceRepository;
	private final AuditEventRepository auditEventRepository;
	private final SystemBootstrapRepository bootstrapRepository;
	private final byte[] expectedTokenDigest;

	InitialWorkspaceBootstrapApplicationService(
			OrganizationUnitRepository organizationRepository,
			WorkspaceRepository workspaceRepository,
			IdentityUserRepository userRepository,
			WorkspaceMembershipRepository membershipRepository,
			UserWorkspacePreferenceRepository preferenceRepository,
			AuditEventRepository auditEventRepository,
			SystemBootstrapRepository bootstrapRepository,
			@Value("${guanseq.bootstrap.token:}") String configuredToken) {
		if (configuredToken.length() < 32) {
			throw new IllegalStateException(
					"GUANSEQ_BOOTSTRAP_TOKEN must contain at least 32 characters when bootstrap is enabled");
		}
		this.organizationRepository = organizationRepository;
		this.workspaceRepository = workspaceRepository;
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.preferenceRepository = preferenceRepository;
		this.auditEventRepository = auditEventRepository;
		this.bootstrapRepository = bootstrapRepository;
		this.expectedTokenDigest = digest(configuredToken);
	}

	@Transactional
	public Response bootstrap(String presentedToken, Request request, String requestId) {
		requireValidToken(presentedToken);
		SystemBootstrapEntity state = bootstrapRepository.lockSingleton()
				.orElseThrow(() -> new IllegalStateException("Identity bootstrap state is missing"));
		if (!state.isPending()
				|| userRepository.count() > 0
				|| organizationRepository.count() > 0
				|| workspaceRepository.count() > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "系统已经初始化，不能重复执行");
		}
		if (request.tenantCode().equals(request.plantCode())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租户公司编码与工厂编码不能相同");
		}

		UUID tenantId = UUID.randomUUID();
		UUID plantId = UUID.randomUUID();
		UUID workspaceId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		OrganizationUnitEntity tenant = organizationRepository.saveAndFlush(new OrganizationUnitEntity(
				tenantId,
				request.tenantCode(),
				request.tenantName(),
				"COMPANY",
				null));
		OrganizationUnitEntity plant = organizationRepository.saveAndFlush(new OrganizationUnitEntity(
				plantId,
				request.plantCode(),
				request.plantName(),
				"PLANT",
				tenantId));
		WorkspaceEntity workspace = workspaceRepository.saveAndFlush(new WorkspaceEntity(
				workspaceId,
				request.workspaceCode(),
				request.workspaceName(),
				tenant,
				plant));
		IdentityUserEntity user = userRepository.saveAndFlush(new IdentityUserEntity(
				userId,
				tenantId,
				request.externalUsername(),
				request.displayName()));
		membershipRepository.save(new WorkspaceMembershipEntity(
				UUID.randomUUID(), user.getId(), workspace.getId(), "ADMIN"));
		preferenceRepository.save(new UserWorkspacePreferenceEntity(user.getId(), workspace.getId()));

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("source", "ONE_TIME_BOOTSTRAP");
		details.put("tenantCode", request.tenantCode());
		details.put("plantCode", request.plantCode());
		details.put("workspaceCode", request.workspaceCode());
		details.put("externalUsername", request.externalUsername());
		auditEventRepository.save(new AuditEventEntity(
				user.getId(),
				workspace.getId(),
				"SYSTEM_BOOTSTRAPPED",
				"WORKSPACE",
				workspace.getId().toString(),
				requestId,
				details));
		state.complete(user.getId(), workspace.getId(), requestId);

		return new Response("COMPLETED", tenantId, plantId, workspaceId, userId, user.getUsername());
	}

	private void requireValidToken(String presentedToken) {
		if (!MessageDigest.isEqual(expectedTokenDigest, digest(presentedToken == null ? "" : presentedToken))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "初始化凭据无效");
		}
	}

	private static byte[] digest(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest is not available", exception);
		}
	}
}
