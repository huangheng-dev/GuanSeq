package com.guanseq.production.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.production.internal.OperationLaborEntryApplicationService;

@RestController
@RequestMapping(path = "/api/v1/production/operation-labor-entries", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperationLaborEntryController {
	private final OperationLaborEntryApplicationService service;

	OperationLaborEntryController(OperationLaborEntryApplicationService service) { this.service = service; }

	@GetMapping
	OperationLaborEntryPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(required = false) UUID taskId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, taskId, page, size);
	}

	@GetMapping("/{id}")
	OperationLaborEntryRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	OperationLaborEntryRecord create(Principal principal,
			@Valid @RequestBody OperationLaborEntryRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PostMapping(path = "/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	OperationLaborEntryRecord action(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody OperationLaborEntryRecord.ActionRequest request) {
		return service.action(principal.getName(), id, request);
	}
}
