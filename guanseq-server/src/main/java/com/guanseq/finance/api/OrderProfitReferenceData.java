package com.guanseq.finance.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderProfitReferenceData(List<SettlableOrder> orders) {
	public record SettlableOrder(
			UUID salesOrderId,
			String orderNumber,
			String customerName,
			String orderStatus,
			BigDecimal orderedQuantity,
			BigDecimal shippedQuantity,
			BigDecimal revenueCandidate,
			boolean settled,
			String settlementId,
			String settlementNumber,
			String costStatus) { }
}
