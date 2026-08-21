package com.guanseq.sales.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guanseq.sales.api.SalesProfitQueryProvider;

@Service
class SalesProfitQueryService implements SalesProfitQueryProvider {

	private final SalesOrderRepository orderRepository;

	SalesProfitQueryService(SalesOrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProfitOrder> listShippedOrders(UUID tenantOrganizationId) {
		return orderRepository
				.findByTenantOrganizationIdAndStatusIn(tenantOrganizationId, List.of("PARTIALLY_SHIPPED", "SHIPPED"),
						Sort.by(Sort.Order.asc("promisedDeliveryDate").nullsLast(), Sort.Order.asc("orderNumber")))
				.stream().map(this::toProfitOrder).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ProfitOrder> findShippedOrder(UUID tenantOrganizationId, UUID salesOrderId) {
		return orderRepository.findByIdAndTenantOrganizationId(salesOrderId, tenantOrganizationId)
				.filter(order -> List.of("PARTIALLY_SHIPPED", "SHIPPED").contains(order.getStatus()))
				.map(this::toProfitOrder);
	}

	private ProfitOrder toProfitOrder(SalesOrderEntity order) {
		return new ProfitOrder(order.getId(), order.getOrderNumber(), order.getCustomerId(), order.getCustomerCode(),
				order.getCustomerName(), order.getCurrency(), order.getStatus(),
				order.getLines().stream().sorted(Comparator.comparingInt(SalesOrderLineEntity::getLineNumber))
						.map(line -> new ProfitLine(line.getId(), line.getLineNumber(), line.getMaterialId(),
								line.getMaterialCode(), line.getMaterialName(), line.getMaterialSpecification(),
								line.getUnit(), line.getQuantity(), line.getDeliveredQuantity(), line.getUnitPrice()))
						.toList());
	}
}
