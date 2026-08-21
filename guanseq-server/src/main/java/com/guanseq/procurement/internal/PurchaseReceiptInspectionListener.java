package com.guanseq.procurement.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.guanseq.quality.api.IncomingInspectionProvider;

@Component
class PurchaseReceiptInspectionListener {
	private final PurchaseReceiptApplicationService service;
	PurchaseReceiptInspectionListener(PurchaseReceiptApplicationService service) { this.service = service; }

	@EventListener
	void onCompleted(IncomingInspectionProvider.CompletedEvent event) {
		service.settleIncomingInspection(event);
	}
}