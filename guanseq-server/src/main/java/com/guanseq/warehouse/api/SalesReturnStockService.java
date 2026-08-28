package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.UUID;

/** 仓储模块向销售退货用例公开的待检收货、质量转移与收货冲回边界。 */
public interface SalesReturnStockService {

	ReturnStockReceipt receiveSalesReturn(ReceiptCommand command);

	InspectionSettlement inspectSalesReturn(InspectionCommand command);

	ReturnStockReceipt reverseSalesReturnReceipt(ReverseCommand command);

	record ReceiptCommand(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID warehouseId, UUID locationId, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, BigDecimal quantity, String lotNumber, UUID sourceId,
			String sourceNumber, UUID sourceLineId, String requestId) { }

	record InspectionCommand(UUID tenantOrganizationId, UUID owningOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID inspectionBalanceId, UUID materialId, String materialCode, String materialName,
			String materialSpecification, String unit, BigDecimal acceptedQuantity, BigDecimal rejectedQuantity,
			String lotNumber, UUID sourceId, String sourceNumber, UUID sourceLineId, String requestId) { }

	record ReverseCommand(UUID tenantOrganizationId, UUID workspaceId, UUID actorUserId,
			UUID inspectionBalanceId, BigDecimal quantity, UUID sourceId, String sourceNumber, UUID sourceLineId,
			String requestId, String reason) { }

	record ReturnStockReceipt(UUID balanceId, UUID movementId, String movementNumber, String warehouseCode,
			String warehouseName, String locationCode, String locationName, String lotNumber, String qualityStatus) { }

	record InspectionSettlement(ReturnStockReceipt accepted, ReturnStockReceipt rejected) { }
}
