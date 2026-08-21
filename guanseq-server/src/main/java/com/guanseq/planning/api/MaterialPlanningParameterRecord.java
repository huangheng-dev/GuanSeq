package com.guanseq.planning.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MaterialPlanningParameterRecord(
		UUID materialId, String materialCode, String materialName, String materialSpecification,
		String procurementType, String unit, Integer leadTimeDays, boolean configured,
		long version, Instant updatedAt) {

	public record UpdateRequest(@Min(1) @Max(3650) int leadTimeDays, long expectedVersion) { }
}
