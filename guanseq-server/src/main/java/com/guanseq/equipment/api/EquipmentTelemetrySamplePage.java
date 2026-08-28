package com.guanseq.equipment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EquipmentTelemetrySamplePage(
		List<Sample> items,
		long totalElements,
		int page,
		int size,
		int totalPages,
		UUID connectionId,
		Instant windowFrom,
		Instant windowTo) {

	public record Sample(
			UUID id,
			UUID pointId,
			String pointCode,
			String rawValue,
			BigDecimal numericValue,
			Boolean booleanValue,
			String quality,
			Instant deviceTime,
			Instant receivedAt,
			long sequenceNumber,
			int messageVersion,
			String sourceProtocol) { }
}
