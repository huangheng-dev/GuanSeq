package com.guanseq.sales.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guanseq.sales.api.SalesReceivableQueryProvider;

@Service
class SalesReceivableQueryService implements SalesReceivableQueryProvider {

	private static final List<String> SHIPPED_STATUSES = List.of("PARTIALLY_SHIPPED", "SHIPPED", "PARTIALLY_RETURNED", "RETURNED");
	private final SalesOrderRepository orderRepository;

	SalesReceivableQueryService(SalesOrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ReceivableOrder> listShippedOrders(UUID tenantOrganizationId) {
		return orderRepository.findByTenantOrganizationIdAndStatusIn(tenantOrganizationId, SHIPPED_STATUSES,
				Sort.by(Sort.Order.asc("promisedDeliveryDate").nullsLast(), Sort.Order.asc("orderNumber")))
				.stream().map(this::toOrder).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ReceivableOrder> findShippedOrder(UUID tenantOrganizationId, UUID salesOrderId) {
		return orderRepository.findByIdAndTenantOrganizationId(salesOrderId, tenantOrganizationId)
				.filter(order -> SHIPPED_STATUSES.contains(order.getStatus())).map(this::toOrder);
	}

	private ReceivableOrder toOrder(SalesOrderEntity order) {
		return new ReceivableOrder(order.getId(), order.getOrderNumber(), order.getCustomerId(), order.getCustomerCode(),
				order.getCustomerName(), order.getCurrency(), order.getTaxRate(), order.getStatus(), order.getLines().stream()
						.sorted(Comparator.comparingInt(SalesOrderLineEntity::getLineNumber))
						.map(line -> new ReceivableLine(line.getId(), line.getLineNumber(), line.getMaterialId(),
								line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(), line.getUnit(),
								line.getQuantity(), line.getDeliveredQuantity(), line.getReturnedQuantity(),
								line.getNetDeliveredQuantity(), line.getUnitPrice()))
						.toList());
	}
}
