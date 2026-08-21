package com.guanseq.procurement.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 采购模块向财务模块公开的合格收货快照，不暴露采购模块持久化实现。 */
public interface ProcurementPayableQueryProvider {

	List<PayableOrder> listReceivedOrders(UUID tenantOrganizationId);

	Optional<PayableOrder> findReceivedOrder(UUID tenantOrganizationId, UUID purchaseOrderId);

	record PayableOrder(
			UUID id,
			String orderNumber,
			UUID supplierId,
			String supplierCode,
			String supplierName,
			String currency,
			BigDecimal taxRate,
			String status,
			List<PayableLine> lines) { }

	record PayableLine(
			UUID id,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal orderedQuantity,
			BigDecimal acceptedQuantity,
			BigDecimal unitPrice) { }
}
