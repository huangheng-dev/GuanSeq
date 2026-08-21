package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockPositionProvider {

	List<StockPosition> getPositions(UUID tenantOrganizationId, Collection<UUID> materialIds);

	record StockPosition(UUID materialId, BigDecimal onHandQuantity, BigDecimal allocatedQuantity,
			BigDecimal frozenQuantity, BigDecimal availableQuantity, int balanceCount) {
	}
}
