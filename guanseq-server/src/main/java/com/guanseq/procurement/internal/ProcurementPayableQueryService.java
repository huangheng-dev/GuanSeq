package com.guanseq.procurement.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guanseq.procurement.api.ProcurementPayableQueryProvider;

@Service
class ProcurementPayableQueryService implements ProcurementPayableQueryProvider {

	private final PurchaseOrderRepository orderRepository;

	ProcurementPayableQueryService(PurchaseOrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PayableOrder> listReceivedOrders(UUID tenantOrganizationId) {
		return orderRepository.findReceivedOrders(tenantOrganizationId).stream().map(this::toOrder).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<PayableOrder> findReceivedOrder(UUID tenantOrganizationId, UUID purchaseOrderId) {
		return orderRepository.findReceivedOrder(purchaseOrderId, tenantOrganizationId).map(this::toOrder);
	}

	private PayableOrder toOrder(PurchaseOrderEntity order) {
		return new PayableOrder(order.getId(), order.getOrderNumber(), order.getSupplierId(), order.getSupplierCode(),
				order.getSupplierName(), order.getCurrency(), order.getTaxRate(), order.getStatus(), order.getLines().stream()
						.filter(line -> line.getReceivedQuantity().signum() > 0)
						.sorted(Comparator.comparingInt(PurchaseOrderLineEntity::getLineNumber))
						.map(line -> new PayableLine(line.getId(), line.getLineNumber(), line.getMaterialId(),
								line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(),
								line.getOrderedQuantity(), line.getReceivedQuantity(), line.getUnitPrice()))
						.toList());
	}
}
