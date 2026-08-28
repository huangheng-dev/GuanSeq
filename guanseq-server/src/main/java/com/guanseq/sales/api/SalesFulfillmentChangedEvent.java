package com.guanseq.sales.api;

import java.util.UUID;

/** 销售模块发布给财务等下游的净履约数量变化事实。 */
public record SalesFulfillmentChangedEvent(String username, UUID salesOrderId, String triggerType,
		String triggerNumber) { }
