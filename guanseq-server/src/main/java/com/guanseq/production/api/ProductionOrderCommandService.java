package com.guanseq.production.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface ProductionOrderCommandService {

	CreatedOrder createFromMrp(String username, CreateFromMrpCommand command);

	record CreateFromMrpCommand(UUID suggestionId, String sourceNumber, UUID materialId, BigDecimal quantity,
			LocalDate plannedStartDate, LocalDate plannedReceiptDate, String workshop, String owner) { }

	record CreatedOrder(UUID id, String orderNumber) { }
}
