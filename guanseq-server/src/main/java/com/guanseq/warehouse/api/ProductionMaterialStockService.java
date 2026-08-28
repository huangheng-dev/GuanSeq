package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductionMaterialStockService {

	IssueResult issueMaterials(IssueCommand command);

	List<AvailableBalance> listAvailableBalances(UUID tenantOrganizationId, Collection<UUID> warehouseIds,
			Collection<UUID> materialIds);

	ReturnResult returnMaterials(ReturnCommand command);

	record IssueCommand(
			UUID tenantOrganizationId,
			UUID owningOrganizationId,
			UUID workspaceId,
			UUID actorUserId,
			String requestId,
			UUID warehouseId,
			String sourceType,
			String sourceNumber,
			List<IssueLine> lines,
			String reason) { }

	record IssueLine(
			UUID sourceId,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal quantity,
			String reason,
			UUID stockBalanceId,
			Long expectedStockVersion) {
		public IssueLine(UUID sourceId, UUID materialId, String materialCode, String materialName,
				String materialSpecification, String unit, BigDecimal quantity, String reason) {
			this(sourceId, materialId, materialCode, materialName, materialSpecification, unit, quantity, reason, null, null);
		}
	}

	record AvailableBalance(
			UUID id,
			UUID warehouseId,
			String warehouseCode,
			UUID locationId,
			String locationCode,
			String locationName,
			UUID materialId,
			String materialCode,
			String lotNumber,
			BigDecimal availableQuantity,
			long version) { }

	record IssueResult(List<StockMovementResult> movements) { }

	record ReturnCommand(
			UUID tenantOrganizationId,
			UUID owningOrganizationId,
			UUID workspaceId,
			UUID actorUserId,
			String requestId,
			UUID warehouseId,
			UUID locationId,
			String sourceType,
			String sourceNumber,
			List<ReturnLine> lines,
			String reason) { }

	record ReturnLine(
			UUID sourceId,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal quantity,
			String reason) { }

	record ReturnResult(List<StockMovementResult> movements) { }

	record StockMovementResult(
			UUID sourceId,
			UUID sourceLineId,
			UUID materialId,
			UUID balanceId,
			UUID movementId,
			String movementNumber,
			UUID warehouseId,
			String warehouseCode,
			String warehouseName,
			UUID locationId,
			String locationCode,
			String locationName,
			String lotNumber,
			BigDecimal quantity) { }
}


