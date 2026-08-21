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

import com.guanseq.production.internal.OperationTaskApplicationService;

@RestController
@RequestMapping(path = "/api/v1/production", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperationTaskController {
	private final OperationTaskApplicationService service;

	OperationTaskController(OperationTaskApplicationService service) { this.service = service; }

	@GetMapping("/operation-tasks")
	OperationTaskPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/operation-tasks/{id}")
	OperationTaskRecord get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }

	@GetMapping("/orders/{orderId}/operation-tasks")
	java.util.List<OperationTaskRecord> byOrder(Principal principal, @PathVariable UUID orderId) {
		return service.listByOrder(principal.getName(), orderId);
	}

	@PostMapping(path = "/operation-tasks/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	OperationTaskRecord action(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody OperationTaskRecord.ActionRequest request) {
		return service.action(principal.getName(), id, request);
	}
}
