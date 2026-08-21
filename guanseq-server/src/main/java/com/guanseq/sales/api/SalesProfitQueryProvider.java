package com.guanseq.sales.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesProfitQueryProvider {

	List<ProfitOrder> listShippedOrders(UUID tenantOrganizationId);

	Optional<ProfitOrder> findShippedOrder(UUID tenantOrganizationId, UUID salesOrderId);

	record ProfitOrder(
			UUID id,
			String orderNumber,
			UUID customerId,
			String customerCode,
			String customerName,
			String currency,
			String status,
			List<ProfitLine> lines) { }

	record ProfitLine(
			UUID id,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal orderedQuantity,
			BigDecimal deliveredQuantity,
			BigDecimal unitPrice) { }
}
