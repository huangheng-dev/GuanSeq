package com.guanseq.sales.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 销售模块向财务模块公开的已发货应收快照，不暴露销售模块持久化实现。 */
public interface SalesReceivableQueryProvider {

	List<ReceivableOrder> listShippedOrders(UUID tenantOrganizationId);

	Optional<ReceivableOrder> findShippedOrder(UUID tenantOrganizationId, UUID salesOrderId);

	record ReceivableOrder(
			UUID id,
			String orderNumber,
			UUID customerId,
			String customerCode,
			String customerName,
			String currency,
			BigDecimal taxRate,
			String status,
			List<ReceivableLine> lines) { }

	record ReceivableLine(
			UUID id,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal orderedQuantity,
			BigDecimal grossDeliveredQuantity,
			BigDecimal returnedQuantity,
			BigDecimal deliveredQuantity,
			BigDecimal unitPrice) { }
}
