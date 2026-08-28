package com.guanseq.finance.internal;

import com.guanseq.sales.api.SalesFulfillmentChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class SalesReturnProfitImpactListener {
	private final OrderProfitApplicationService orderProfitService;

	SalesReturnProfitImpactListener(OrderProfitApplicationService orderProfitService) {
		this.orderProfitService = orderProfitService;
	}

	@EventListener
	void onSalesFulfillmentChanged(SalesFulfillmentChangedEvent event) {
		orderProfitService.markImpactedIfSettled(event.username(), event.salesOrderId(), event.triggerType(),
				event.triggerNumber());
	}
}
