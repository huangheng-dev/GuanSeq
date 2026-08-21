package com.guanseq.quality.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface IncomingInspectionProvider {
	Inspection create(CreateCommand command);
	Optional<Inspection> findIncoming(UUID tenantOrganizationId, UUID inspectionId);

	record CreateCommand(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID receiptId, String receiptNumber, UUID receiptLineId, UUID purchaseOrderId, String purchaseOrderNumber,
			UUID supplierId, String supplierCode, String supplierName, UUID materialId, String materialCode,
			String materialName, String materialSpecification, String unit, BigDecimal inspectionQuantity,
			String requestId) { }

	record Inspection(UUID id, String inspectionNumber, String status, String result,
			BigDecimal acceptedQuantity, BigDecimal rejectedQuantity) { }

	record CompletedEvent(UUID tenantOrganizationId, UUID actorUserId, UUID inspectionId, UUID receiptLineId,
			UUID purchaseOrderId, UUID materialId, BigDecimal acceptedQuantity, BigDecimal rejectedQuantity,
			String requestId) { }
}
