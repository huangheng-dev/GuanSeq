package com.guanseq.product.api;

import java.util.List;
import java.util.UUID;

public record RoutingReferenceData(List<MaterialOption> materials) {

	public record MaterialOption(UUID id, String code, String name, String specification, String baseUnit,
			String procurementType) {
	}
}
