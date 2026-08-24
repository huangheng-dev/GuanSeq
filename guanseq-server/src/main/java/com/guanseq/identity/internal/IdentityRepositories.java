package com.guanseq.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface OrganizationUnitRepository extends JpaRepository<OrganizationUnitEntity, UUID> {
}

interface IdentityUserRepository extends JpaRepository<IdentityUserEntity, UUID> {

	Optional<IdentityUserEntity> findByUsernameAndStatus(String username, String status);
}

interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {

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
