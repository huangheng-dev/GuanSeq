package com.guanseq.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface OrganizationUnitRepository extends JpaRepository<OrganizationUnitEntity, UUID> {
	List<OrganizationUnitEntity> findAllByParentIdOrderByName(UUID parentId);
	Optional<OrganizationUnitEntity> findByCode(String code);
}

interface IdentityUserRepository extends JpaRepository<IdentityUserEntity, UUID> {

	Optional<IdentityUserEntity> findByUsernameAndStatus(String username, String status);

	Optional<IdentityUserEntity> findByUsername(String username);

	Optional<IdentityUserEntity> findByIdAndTenantOrganizationId(UUID id, UUID tenantOrganizationId);
}

interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
	long countByOperatingOrganizationAndStatus(OrganizationUnitEntity operatingOrganization, String status);

	@Query("""
			select w from WorkspaceEntity w
			join fetch w.tenantOrganization
			join fetch w.operatingOrganization
			where w.status = 'ACTIVE'
			and exists (
				select m.id from WorkspaceMembershipEntity m
				where m.userId = :userId
				and m.workspaceId = w.id
				and m.status = 'ACTIVE'
			)
			order by w.name
			""")
	List<WorkspaceEntity> findAccessibleByUserId(@Param("userId") UUID userId);
}

interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembershipEntity, UUID> {
	List<WorkspaceMembershipEntity> findAllByWorkspaceIdOrderByCreatedAt(UUID workspaceId);
	long countByWorkspaceIdAndOrganizationUnitIdAndStatus(UUID workspaceId, UUID organizationUnitId, String status);

	Optional<WorkspaceMembershipEntity> findByUserIdAndWorkspaceIdAndStatus(
			UUID userId,
			UUID workspaceId,
			String status);

	@Query("""
			select m.roleCode from WorkspaceMembershipEntity m
			where m.userId = :userId
			and m.workspaceId = :workspaceId
			and m.status = 'ACTIVE'
			""")
	Optional<String> findRoleCode(
			@Param("userId") UUID userId,
			@Param("workspaceId") UUID workspaceId);

	Optional<WorkspaceMembershipEntity> findByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);

	@Query(value = """
			select membership from WorkspaceMembershipEntity membership, IdentityUserEntity identityUser
			where identityUser.id = membership.userId
			and membership.workspaceId = :workspaceId
			and (:status = 'ALL' or membership.status = :status)
			and (:query = '' or lower(identityUser.username) like lower(concat('%', :query, '%'))
				or lower(identityUser.displayName) like lower(concat('%', :query, '%')))
			order by identityUser.displayName, identityUser.username
			""",
			countQuery = """
			select count(membership) from WorkspaceMembershipEntity membership, IdentityUserEntity identityUser
			where identityUser.id = membership.userId
			and membership.workspaceId = :workspaceId
			and (:status = 'ALL' or membership.status = :status)
			and (:query = '' or lower(identityUser.username) like lower(concat('%', :query, '%'))
				or lower(identityUser.displayName) like lower(concat('%', :query, '%')))
			""")
	Page<WorkspaceMembershipEntity> findWorkspacePage(
			@Param("workspaceId") UUID workspaceId,
			@Param("query") String query,
			@Param("status") String status,
			Pageable pageable);

	long countByWorkspaceIdAndRoleCodeAndStatus(UUID workspaceId, String roleCode, String status);
}

interface UserWorkspacePreferenceRepository extends JpaRepository<UserWorkspacePreferenceEntity, UUID> {
}

interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
}

interface SystemBootstrapRepository extends JpaRepository<SystemBootstrapEntity, Boolean> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select state from SystemBootstrapEntity state where state.singletonKey = true")
	Optional<SystemBootstrapEntity> lockSingleton();
}
