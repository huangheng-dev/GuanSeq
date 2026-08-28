package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabelStockBalanceReferenceProvider {

	Optional<StockBalanceLabelReference> findLabelStock(UUID tenantOrganizationId, UUID balanceId);

	List<StockBalanceLabelReference> listLabelStocks(UUID tenantOrganizationId, int limit);

	record StockBalanceLabelReference(UUID id, long version, String materialCode, String materialName,
			String warehouseCode, String locationCode, String lotNumber, String qualityStatus,
			BigDecimal onHandQuantity, String unit) { }
}

