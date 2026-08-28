package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseReturnStockService {
	Optional<Availability> findAvailability(UUID tenantOrganizationId, UUID balanceId);
	Movement ship(ReturnCommand command);
	Movement reverse(ReturnCommand command);

	record Availability(UUID balanceId, String warehouseCode, String warehouseName, String locationCode,
			String locationName, String lotNumber, String qualityStatus, BigDecimal returnableQuantity) { }

	record ReturnCommand(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId, UUID balanceId,
			String expectedQualityStatus, BigDecimal quantity, UUID sourceId, String sourceNumber,
			UUID sourceLineId, String reason, String requestId) { }

	record Movement(UUID balanceId, UUID movementId, String movementNumber, String warehouseCode,
			String warehouseName, String locationCode, String locationName, String lotNumber, String qualityStatus) { }
}
