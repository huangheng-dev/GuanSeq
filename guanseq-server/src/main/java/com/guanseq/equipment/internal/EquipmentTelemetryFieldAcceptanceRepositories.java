package com.guanseq.equipment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface EquipmentTelemetryFieldAcceptanceRepository
		extends JpaRepository<EquipmentTelemetryFieldAcceptanceEntity, UUID> {
	Optional<EquipmentTelemetryFieldAcceptanceEntity> findByConnectionId(UUID connectionId);
	Optional<EquipmentTelemetryFieldAcceptanceEntity> findByTenantOrganizationIdAndWorkspaceIdAndCreationRequestId(
			UUID tenantOrganizationId, UUID workspaceId, String creationRequestId);
}

interface EquipmentTelemetryFieldAcceptanceEventRepository
		extends JpaRepository<EquipmentTelemetryFieldAcceptanceEventEntity, UUID> {
	List<EquipmentTelemetryFieldAcceptanceEventEntity> findByAcceptanceIdOrderByOccurredAtDesc(UUID acceptanceId);
	Optional<EquipmentTelemetryFieldAcceptanceEventEntity> findByAcceptanceIdAndRequestId(
			UUID acceptanceId, String requestId);
}
