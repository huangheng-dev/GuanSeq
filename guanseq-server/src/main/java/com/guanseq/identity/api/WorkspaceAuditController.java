package com.guanseq.identity.api;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.identity.internal.WorkspaceAuditApplicationService;

@Validated
@RestController
@RequestMapping(path = "/api/v1/identity/audit-events", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkspaceAuditController {
	private final WorkspaceAuditApplicationService service;

	WorkspaceAuditController(WorkspaceAuditApplicationService service) {
		this.service = service;
	}

	@GetMapping
	WorkspaceAuditPage list(
			Principal principal,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "") @Size(max = 80) String eventType,
			@RequestParam(defaultValue = "") @Size(max = 80) String objectType,
			@RequestParam(required = false) UUID actorId,
			@RequestParam(defaultValue = "") @Size(max = 120) String query,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo) {
		return service.list(principal.getName(), page, size, eventType, objectType, actorId, query, occurredFrom, occurredTo);
	}
}
