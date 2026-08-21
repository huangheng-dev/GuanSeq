package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface FinishedGoodsReceiptService {
	Receipt receive(Command command);
	Optional<Receipt> findBySource(UUID tenantOrganizationId, UUID sourceId);

	record Command(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID warehouseId, UUID locationId, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, BigDecimal quantity, String lotNumber,
			UUID sourceId, String sourceNumber, String requestId) { }

	record Receipt(UUID balanceId, UUID movementId, String movementNumber, String warehouseCode,
			String warehouseName, String locationCode, String locationName, String lotNumber) { }
}
