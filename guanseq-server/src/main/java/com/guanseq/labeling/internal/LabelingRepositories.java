package com.guanseq.labeling.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LabelPrintRequestRepository extends JpaRepository<LabelPrintRequestEntity, UUID> {
	@Query("""
			select r from LabelPrintRequestEntity r where r.tenantOrganizationId = :tenantId and r.workspaceId = :workspaceId
			and (:objectType = '' or r.objectType = :objectType)
			and (:query = '' or lower(r.requestNumber) like lower(concat('%', :query, '%'))
				or lower(r.objectCode) like lower(concat('%', :query, '%'))
				or lower(r.objectName) like lower(concat('%', :query, '%'))
				or lower(r.payload) like lower(concat('%', :query, '%')))
			""")
	Page<LabelPrintRequestEntity> search(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId,
			@Param("query") String query, @Param("objectType") String objectType, Pageable pageable);

	Optional<LabelPrintRequestEntity> findByTenantOrganizationIdAndRequestId(UUID tenantId, String requestId);
	boolean existsByTenantOrganizationIdAndWorkspaceIdAndObjectTypeAndObjectId(UUID tenantId, UUID workspaceId,
			String objectType, UUID objectId);
	List<LabelPrintRequestEntity> findByTenantOrganizationIdAndWorkspaceIdAndObjectTypeAndObjectIdIn(UUID tenantId,
			UUID workspaceId, String objectType, List<UUID> objectIds);
}

