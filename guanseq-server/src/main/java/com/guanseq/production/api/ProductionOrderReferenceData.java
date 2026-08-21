package com.guanseq.production.api;

import java.util.List;
import java.util.UUID;

public record ProductionOrderReferenceData(List<MaterialOption> materials) {
	public record MaterialOption(UUID id, String code, String name, String specification, String baseUnit) { }
}
