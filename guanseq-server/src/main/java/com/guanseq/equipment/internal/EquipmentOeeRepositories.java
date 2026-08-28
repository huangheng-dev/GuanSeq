package com.guanseq.equipment.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentOeeRecordRepository extends JpaRepository<EquipmentOeeRecordEntity, UUID> {
	@Query("""
			select record from EquipmentOeeRecordEntity record
			where record.tenantOrganizationId = :tenantId and record.workspaceId = :workspaceId
			and (:status = '' or record.status = :status)
			and (:query = ''
				or lower(record.recordNumber) like lower(concat('%', :query, '%'))
				or lower(record.assetCodeSnapshot) like lower(concat('%', :query, '%'))
				or lower(record.assetNameSnapshot) like lower(concat('%', :query, '%'))
				or lower(record.shiftName) like lower(concat('%', :query, '%'))
				or lower(coalesce(record.productionReference, '')) like lower(concat('%', :query, '%')))
			""")
	Page<EquipmentOeeRecordEntity> search(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId,
			@Param("query") String query, @Param("status") String status, Pageable pageable);

	Optional<EquipmentOeeRecordEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(UUID id, UUID tenantId,
			UUID workspaceId);
	Optional<EquipmentOeeRecordEntity> findByTenantOrganizationIdAndCreationRequestId(UUID tenantId, String requestId);
	List<EquipmentOeeRecordEntity> findByTenantOrganizationIdAndWorkspaceIdAndStatus(UUID tenantId, UUID workspaceId,
			String status);

	@Query("""
			select count(record) from EquipmentOeeRecordEntity record
			where record.tenantOrganizationId = :tenantId and record.workspaceId = :workspaceId
			and record.assetId = :assetId and record.id <> :id
			and record.status in ('SUBMITTED', 'APPROVED')
			and record.windowStart < :windowEnd and record.windowEnd > :windowStart
			""")
	long countOverlappingSubmitted(@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId,
			@Param("assetId") UUID assetId, @Param("id") UUID id,
			@Param("windowStart") Instant windowStart, @Param("windowEnd") Instant windowEnd);
}

interface EquipmentOeeDowntimeRepository extends JpaRepository<EquipmentOeeDowntimeEntity, UUID> {
	List<EquipmentOeeDowntimeEntity> findByOeeRecordIdOrderByStartedAt(UUID recordId);
	Optional<EquipmentOeeDowntimeEntity> findByIdAndOeeRecordId(UUID id, UUID recordId);
}

interface EquipmentOeeEventRepository extends JpaRepository<EquipmentOeeEventEntity, UUID> {
	List<EquipmentOeeEventEntity> findByOeeRecordIdOrderByOccurredAtDesc(UUID recordId);
	Optional<EquipmentOeeEventEntity> findByOeeRecordIdAndRequestId(UUID recordId, String requestId);
}
