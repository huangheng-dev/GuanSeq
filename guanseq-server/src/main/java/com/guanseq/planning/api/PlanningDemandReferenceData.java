package com.guanseq.planning.api;

import java.util.List;
import java.util.UUID;

public record PlanningDemandReferenceData(List<MaterialOption> materials) {

	public record MaterialOption(
			UUID id,
			String code,
			String name,
			String specification,
			String baseUnit) {
	}
}
