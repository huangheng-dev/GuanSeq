package com.guanseq.production.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductionScheduledReceiptProvider {
	List<ScheduledReceipt> listReleasedProductionReceipts(UUID tenantOrganizationId,
			Collection<UUID> materialIds, LocalDate horizonEnd);

	record ScheduledReceipt(UUID orderId, String orderNumber, String workshop, UUID materialId,
			String materialCode, String materialName, String unit, BigDecimal outstandingQuantity,
			LocalDate expectedReceiptDate) { }
}
