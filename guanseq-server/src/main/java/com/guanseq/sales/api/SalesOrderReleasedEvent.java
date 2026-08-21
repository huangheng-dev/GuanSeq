package com.guanseq.sales.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesOrderReleasedEvent(
		UUID tenantOrganizationId,
		UUID owningOrganizationId,
		UUID workspaceId,
		UUID actorUserId,
		UUID orderId,
		String orderNumber,
		String customerName,
		LocalDate requiredDate,
		String owner,
		Instant occurredAt,
		List<Line> lines) {

	public record Line(
			UUID lineId,
			int lineNumber,
			UUID materialId,
			String materialCode,
			String materialName,
			String materialSpecification,
			String unit,
			BigDecimal quantity) {
	}
}
