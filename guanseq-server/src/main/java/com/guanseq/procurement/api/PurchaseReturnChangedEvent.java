package com.guanseq.procurement.api;

import java.util.UUID;

public record PurchaseReturnChangedEvent(String username, UUID purchaseOrderId, String triggerType,
		String triggerNumber) { }
