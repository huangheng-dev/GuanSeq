package com.guanseq.sales.api;

import java.util.List;
import java.util.UUID;

public record SalesOrderReferenceData(
		List<CustomerOption> customers,
		List<MaterialOption> materials) {

	public record CustomerOption(UUID id, String code, String name, String creditLevel) {
	}

	public record MaterialOption(UUID id, String code, String name, String specification, String baseUnit) {
	}
}
