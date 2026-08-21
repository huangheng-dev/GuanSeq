package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface PurchaseReceiptStockService {
	StockReceipt receiveInspection(Command command);
	StockReceipt receiveAvailable(Command command);
	Settlement settleInspection(SettleCommand command);

	record Command(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID warehouseId, UUID locationId, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, BigDecimal quantity, String lotNumber, UUID sourceId,
			String sourceNumber, UUID sourceLineId, String requestId) { }

	record SettleCommand(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID inspectionBalanceId, UUID warehouseId, UUID locationId, UUID materialId, String materialCode,
			String materialName, String materialSpecification, String unit, BigDecimal acceptedQuantity,
			BigDecimal rejectedQuantity, String lotNumber, UUID sourceId, String sourceNumber, UUID sourceLineId,
			String requestId) { }

	record StockReceipt(UUID balanceId, UUID movementId, String movementNumber, String warehouseCode,
			String warehouseName, String locationCode, String locationName, String lotNumber, String qualityStatus) { }

	record Settlement(StockReceipt accepted, StockReceipt rejected) { }
}
