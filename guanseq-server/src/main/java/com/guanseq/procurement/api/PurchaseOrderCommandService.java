package com.guanseq.procurement.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface PurchaseOrderCommandService {

	CreatedOrder createFromMrp(String username, CreateFromMrpCommand command);

	record CreateFromMrpCommand(UUID suggestionId, String sourceNumber, UUID materialId, BigDecimal quantity,
			UUID supplierId, String currency, BigDecimal taxRate, BigDecimal unitPrice,
			LocalDate requestedReceiptDate, String buyer) { }

	record CreatedOrder(UUID id, String orderNumber) { }
}
