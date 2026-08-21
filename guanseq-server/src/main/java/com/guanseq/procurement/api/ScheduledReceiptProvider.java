package com.guanseq.procurement.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScheduledReceiptProvider {

	List<ScheduledReceipt> listReleasedPurchaseReceipts(UUID tenantOrganizationId, Collection<UUID> materialIds,
			LocalDate horizonEnd);

	record ScheduledReceipt(UUID orderId, String orderNumber, UUID lineId, String supplierName, UUID materialId,
			String materialCode, String materialName, String unit, BigDecimal outstandingQuantity,
			LocalDate expectedReceiptDate) { }
}
