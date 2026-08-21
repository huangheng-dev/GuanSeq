package com.guanseq.production.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MaterialReturnRecord(
		UUID id,
		String returnNumber,
		UUID issueId,
		String issueNumber,
		UUID productionOrderId,
		String orderNumber,
		UUID warehouseId,
		String warehouseCode,
		String warehouseName,
		UUID locationId,
		String locationCode,
		String locationName,
		String reason,
		Instant createdAt,
		List<Line> lines) {

	public record Line(
			UUID id,
			UUID issueLineId,
			int lineNumber,
			UUID componentMaterialId,
			String componentMaterialCode,
			String componentMaterialName,
			String componentMaterialSpecification,
			String unit,
			BigDecimal quantity,
			String reason) { }
}
