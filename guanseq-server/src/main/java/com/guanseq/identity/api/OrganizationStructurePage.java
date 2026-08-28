package com.guanseq.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrganizationStructurePage(
		UUID currentUserId,
		OrganizationUnitRecord company,
		OrganizationUnitRecord operatingUnit,
		List<OrganizationUnitRecord> siteUnits,
		WorkspaceRecord workspace,
		List<MemberRecord> members,
		String scopeDescription) {

	public record OrganizationUnitRecord(
			UUID id, String code, String name, String unitType, UUID parentId, String status,
			UUID responsibleUserId, String responsibleUserName, long version, Instant createdAt, Instant updatedAt) {}

	public record WorkspaceRecord(
			UUID id, String code, String name, String status, UUID operatingOrganizationId,
			UUID responsibleUserId, String responsibleUserName, long version, Instant createdAt, Instant updatedAt) {}

	public record MemberRecord(
			UUID userId, String username, String displayName, String roleCode, String membershipStatus,
			UUID organizationUnitId, String organizationUnitName, long membershipVersion) {}
}
