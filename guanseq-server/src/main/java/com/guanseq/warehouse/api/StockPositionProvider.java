package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockPositionProvider {

	List<StockPosition> getPositions(UUID tenantOrganizationId, Collection<UUID> materialIds);

	List<WarehouseStockPosition> getWarehousePositions(UUID tenantOrganizationId, UUID warehouseId,
			Collection<UUID> materialIds);

	record StockPosition(UUID materialId, BigDecimal onHandQuantity, BigDecimal allocatedQuantity,
			BigDecimal frozenQuantity, BigDecimal availableQuantity, int balanceCount) {
	}

	record WarehouseStockPosition(UUID warehouseId, UUID materialId, BigDecimal onHandQuantity,
			BigDecimal allocatedQuantity, BigDecimal frozenQuantity, BigDecimal availableQuantity, int balanceCount) { }
}
