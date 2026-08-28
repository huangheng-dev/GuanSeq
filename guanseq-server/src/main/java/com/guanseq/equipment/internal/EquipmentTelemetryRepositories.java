package com.guanseq.equipment.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentTelemetryConnectionRepository extends JpaRepository<EquipmentTelemetryConnectionEntity, UUID> {
	Page<EquipmentTelemetryConnectionEntity> findByTenantOrganizationIdAndWorkspaceId(
			UUID tenantOrganizationId, UUID workspaceId, Pageable pageable);

	Optional<EquipmentTelemetryConnectionEntity> findByIdAndTenantOrganizationIdAndWorkspaceId(
			UUID id, UUID tenantOrganizationId, UUID workspaceId);

	List<EquipmentTelemetryConnectionEntity> findTop50ByStatusOrderByUpdatedAtAsc(String status);
}

interface EquipmentTelemetryPointRepository extends JpaRepository<EquipmentTelemetryPointEntity, UUID> {
	List<EquipmentTelemetryPointEntity> findByConnectionIdOrderBySortOrder(UUID connectionId);
	Optional<EquipmentTelemetryPointEntity> findByIdAndConnectionId(UUID id, UUID connectionId);
}

interface EquipmentTelemetryRuntimeRepository extends JpaRepository<EquipmentTelemetryRuntimeEntity, UUID> { }

interface EquipmentTelemetrySampleRepository extends JpaRepository<EquipmentTelemetrySampleEntity, Long> {
	boolean existsByConnectionIdAndPointIdAndSourceMessageId(
			UUID connectionId, UUID pointId, String sourceMessageId);

	@Query(value = """
			select distinct on (point_id) *
			from equipment.telemetry_samples
			where connection_id = :connectionId
			order by point_id, received_at desc, sequence_number desc
			""", nativeQuery = true)
	List<EquipmentTelemetrySampleEntity> findLatestByConnectionId(@Param("connectionId") UUID connectionId);

	@Query(value = """
			select *
			from equipment.telemetry_samples
			where tenant_organization_id = :tenantId
			  and workspace_id = :workspaceId
			  and connection_id = :connectionId
			  and received_at >= :windowFrom
			  and received_at <= :windowTo
			  and (:pointCode is null or point_code = :pointCode)
			  and (:quality is null or quality = :quality)
			order by received_at desc, sequence_number desc
			""", countQuery = """
			select count(*)
			from equipment.telemetry_samples
			where tenant_organization_id = :tenantId
			  and workspace_id = :workspaceId
			  and connection_id = :connectionId
			  and received_at >= :windowFrom
			  and received_at <= :windowTo
			  and (:pointCode is null or point_code = :pointCode)
			  and (:quality is null or quality = :quality)
			""", nativeQuery = true)
	Page<EquipmentTelemetrySampleEntity> findHistory(
			@Param("tenantId") UUID tenantId,
			@Param("workspaceId") UUID workspaceId,
			@Param("connectionId") UUID connectionId,
			@Param("windowFrom") Instant windowFrom,
			@Param("windowTo") Instant windowTo,
			@Param("pointCode") String pointCode,
			@Param("quality") String quality,
			Pageable pageable);

	long countByTenantOrganizationIdAndWorkspaceIdAndReceivedAtBefore(
			UUID tenantOrganizationId, UUID workspaceId, Instant cutoffAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			delete from EquipmentTelemetrySampleEntity sample
			where sample.tenantOrganizationId = :tenantId
			  and sample.workspaceId = :workspaceId
			  and sample.receivedAt < :cutoffAt
			""")
	int deleteExpired(@Param("tenantId") UUID tenantId,
			@Param("workspaceId") UUID workspaceId,
			@Param("cutoffAt") Instant cutoffAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			delete from equipment.telemetry_samples
			where sequence_number in (
			  select sequence_number from equipment.telemetry_samples
			  where tenant_organization_id = :tenantId
			    and workspace_id = :workspaceId
			    and received_at < :cutoffAt
			  order by received_at, sequence_number
			  limit :batchSize
			)
			""", nativeQuery = true)
	int deleteExpiredBatch(@Param("tenantId") UUID tenantId,
			@Param("workspaceId") UUID workspaceId,
			@Param("cutoffAt") Instant cutoffAt,
			@Param("batchSize") int batchSize);
}

interface EquipmentTelemetryConnectionEventRepository
		extends JpaRepository<EquipmentTelemetryConnectionEventEntity, UUID> {
	List<EquipmentTelemetryConnectionEventEntity> findByConnectionIdOrderByOccurredAtDesc(UUID connectionId);
}

interface EquipmentTelemetryRetentionPolicyRepository
		extends JpaRepository<EquipmentTelemetryRetentionPolicyEntity, UUID> {
	Optional<EquipmentTelemetryRetentionPolicyEntity> findByTenantOrganizationIdAndWorkspaceId(
			UUID tenantOrganizationId, UUID workspaceId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select policy from EquipmentTelemetryRetentionPolicyEntity policy
			where policy.tenantOrganizationId = :tenantId and policy.workspaceId = :workspaceId
			""")
	Optional<EquipmentTelemetryRetentionPolicyEntity> findForUpdate(
			@Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select policy from EquipmentTelemetryRetentionPolicyEntity policy where policy.id = :id")
	Optional<EquipmentTelemetryRetentionPolicyEntity> findByIdForUpdate(@Param("id") UUID id);

	List<EquipmentTelemetryRetentionPolicyEntity>
			findTop100ByAutomaticCleanupEnabledTrueAndNextCleanupAtLessThanEqualOrderByNextCleanupAtAsc(Instant now);
}

interface EquipmentTelemetryRetentionEventRepository
		extends JpaRepository<EquipmentTelemetryRetentionEventEntity, UUID> {
	Optional<EquipmentTelemetryRetentionEventEntity> findByTenantOrganizationIdAndWorkspaceIdAndRequestId(
			UUID tenantOrganizationId, UUID workspaceId, String requestId);

	List<EquipmentTelemetryRetentionEventEntity>
			findTop20ByTenantOrganizationIdAndWorkspaceIdOrderByOccurredAtDesc(
					UUID tenantOrganizationId, UUID workspaceId);
}

interface EquipmentTelemetryRetentionRunRepository
		extends JpaRepository<EquipmentTelemetryRetentionRunEntity, UUID> {
	Optional<EquipmentTelemetryRetentionRunEntity> findByTenantOrganizationIdAndWorkspaceIdAndRequestId(
			UUID tenantOrganizationId, UUID workspaceId, String requestId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select run from EquipmentTelemetryRetentionRunEntity run
			where run.id = :id and run.tenantOrganizationId = :tenantId and run.workspaceId = :workspaceId
			""")
	Optional<EquipmentTelemetryRetentionRunEntity> findForUpdate(
			@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("workspaceId") UUID workspaceId);

	List<EquipmentTelemetryRetentionRunEntity>
			findTop20ByTenantOrganizationIdAndWorkspaceIdOrderByStartedAtDesc(
					UUID tenantOrganizationId, UUID workspaceId);
}
