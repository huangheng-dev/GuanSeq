package com.guanseq.quality.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NonconformanceRecord(
		UUID id,
		String caseNumber,
		String sourceType,
		UUID inspectionId,
		String inspectionNumber,
		UUID sourceDocumentId,
		String sourceDocumentNumber,
		UUID orderId,
		String orderNumber,
		UUID supplierId,
		String supplierCode,
		String supplierName,
		UUID materialId,
		String materialCode,
		String materialName,
		String materialSpecification,
		String unit,
		BigDecimal nonconformingQuantity,
		String defectDescription,
		String status,
		String severity,
		String immediateContainment,
		String reviewConclusion,
		Boolean capaRequired,
		String dispositionType,
		String dispositionDecision,
		String dispositionEvidence,
		String dispositionOwner,
		String rootCause,
		String correctiveAction,
		String actionOwner,
		LocalDate actionDueDate,
		boolean overdue,
		String actionCompletionEvidence,
		Boolean verificationEffective,
		String verificationConclusion,
		long version,
		Instant createdAt,
		Instant updatedAt,
		Instant closedAt,
		List<Event> events) {

	public record Event(
			UUID id,
			String action,
			String fromStatus,
			String toStatus,
			UUID actorUserId,
			String actorUsername,
			String reason,
			String requestId,
			Map<String, Object> details,
			Instant occurredAt) { }
}
