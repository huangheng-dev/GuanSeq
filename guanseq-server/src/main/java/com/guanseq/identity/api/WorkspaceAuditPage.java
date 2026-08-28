package com.guanseq.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkspaceAuditPage(
		UUID workspaceId,
		String workspaceCode,
		String workspaceName,
		String companyName,
		String scopeDescription,
		Instant occurredFrom,
		Instant occurredTo,
		List<AuditEventRecord> items,
		long totalElements,
		int page,
		int size,
		int totalPages,
		List<String> eventTypes,
		List<String> objectTypes,
		List<ActorOption> actors) {

	public record AuditEventRecord(
			UUID id,
			String eventType,
			String objectType,
			String objectId,
			String requestId,
			UUID actorId,
			String actorUsername,
			String actorDisplayName,
			Map<String, Object> details,
			Instant occurredAt) {}

	public record ActorOption(UUID id, String username, String displayName) {}
}
