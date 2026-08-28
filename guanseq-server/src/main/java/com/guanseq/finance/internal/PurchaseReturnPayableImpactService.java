package com.guanseq.finance.internal;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.guanseq.identity.api.CurrentWorkspaceProvider;
import com.guanseq.procurement.api.ProcurementPayableQueryProvider;
import com.guanseq.procurement.api.PurchaseReturnChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PurchaseReturnPayableImpactService {
	private final CurrentWorkspaceProvider workspaceProvider;
	private final ProcurementPayableQueryProvider procurementProvider;
	private final PayableInvoiceRepository invoiceRepository;
	private final PayableCreditNoteRepository creditNoteRepository;
	private final PayableEventRepository eventRepository;
	PurchaseReturnPayableImpactService(CurrentWorkspaceProvider workspaceProvider,ProcurementPayableQueryProvider procurementProvider,
			PayableInvoiceRepository invoiceRepository,PayableCreditNoteRepository creditNoteRepository,PayableEventRepository eventRepository){
		this.workspaceProvider=workspaceProvider;this.procurementProvider=procurementProvider;this.invoiceRepository=invoiceRepository;
		this.creditNoteRepository=creditNoteRepository;this.eventRepository=eventRepository;
	}
	@EventListener
	@Transactional
	void onPurchaseReturnChanged(PurchaseReturnChangedEvent event){refresh(event.username(),event.purchaseOrderId(),event.triggerType(),event.triggerNumber());}

	@Transactional
	void refresh(String username,UUID purchaseOrderId,String triggerType,String triggerNumber){
		var access=workspaceProvider.resolve(username);var invoices=invoiceRepository.findByTenantOrganizationIdAndPurchaseOrderId(access.tenantOrganizationId(),purchaseOrderId);
		if(invoices.isEmpty())return;
		Map<UUID,BigDecimal> netInvoiced=new HashMap<>();
		for(PayableInvoiceEntity invoice:invoices){
			for(PayableInvoiceLineEntity line:invoice.getLines())netInvoiced.merge(line.getPurchaseOrderLineId(),line.getInvoiceQuantity(),BigDecimal::add);
			for(PayableCreditNoteEntity note:creditNoteRepository.findByTenantOrganizationIdAndOriginalInvoiceId(access.tenantOrganizationId(),invoice.getId()))
				for(PayableCreditNoteLineEntity line:note.getLines())netInvoiced.merge(line.getPurchaseOrderLineId(),line.getCreditQuantity().negate(),BigDecimal::add);
		}
		Map<UUID,BigDecimal> accepted=new HashMap<>();
		procurementProvider.findReceivedOrder(access.tenantOrganizationId(),purchaseOrderId).ifPresent(order->order.lines().forEach(line->accepted.put(line.id(),line.acceptedQuantity())));
		boolean reviewRequired=netInvoiced.entrySet().stream().anyMatch(entry->entry.getValue().compareTo(accepted.getOrDefault(entry.getKey(),BigDecimal.ZERO))>0);
		for(PayableInvoiceEntity invoice:invoices)if(invoice.markPurchaseReturnImpact(reviewRequired,access.userId())){
			invoiceRepository.saveAndFlush(invoice);
			eventRepository.saveAndFlush(new PayableEventEntity(access.tenantOrganizationId(),access.workspaceId(),access.userId(),invoice.getId(),null,
					"PURCHASE_RETURN_IMPACT",invoice.getStatus(),invoice.getStatus(),"purchase-return-impact-"+triggerNumber+"-"+invoice.getId(),
					Map.of("triggerType",triggerType,"triggerNumber",triggerNumber,"impactStatus",invoice.getPurchaseReturnImpactStatus())));
		}
	}
}
