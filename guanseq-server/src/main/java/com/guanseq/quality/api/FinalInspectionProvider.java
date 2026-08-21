package com.guanseq.quality.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface FinalInspectionProvider {
	Inspection create(CreateCommand command);
	Optional<Inspection> find(UUID tenantOrganizationId, UUID inspectionId);

	record CreateCommand(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID sourceId, String sourceNumber, UUID orderId, String orderNumber, UUID materialId,
			String materialCode, String materialName, String materialSpecification, String unit,
			BigDecimal inspectionQuantity, String requestId) { }

	record Inspection(UUID id, String inspectionNumber, String status, String result,
			BigDecimal acceptedQuantity, BigDecimal rejectedQuantity) { }
}
