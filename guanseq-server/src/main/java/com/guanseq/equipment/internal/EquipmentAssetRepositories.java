package com.guanseq.equipment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentAssetRepository extends JpaRepository<EquipmentAssetEntity, UUID> {
	@Query("""
			select asset from EquipmentAssetEntity asset
			where asset.tenantOrganizationId = :tenantId and asset.workspaceId = :workspaceId
			and (:status = '' or asset.operatingStatus = :status)
			and (:category = '' or asset.category = :category)
			and (:query = ''
				or lower(asset.assetCode) like lower(concat('%', :query, '%'))
				or lower(asset.assetName) like lower(concat('%', :query, '%'))
				or lower(asset.location) like lower(concat('%', :query, '%'))
				or lower(asset.responsiblePerson) like lower(concat('%', :query, '%'))
				or lower(coalesce(asset.workCenterCode, '')) like lower(concat('%', :query, '%'))
				or lower(coalesce(asset.workCenterName, '')) like lower(concat('%', :query, '%')))
			""")
	Page<EquipmentAssetEntity> search(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId,
			@Param("query") String query, @Param("status") String status, @Param("category") String category,
			Pageable pageable);

	Optional<EquipmentAssetEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId, UUID workspaceId);
}

interface EquipmentAssetEventRepository extends JpaRepository<EquipmentAssetEventEntity, UUID> {
	List<EquipmentAssetEventEntity> findByAssetIdOrderByOccurredAtDesc(UUID assetId);
}
