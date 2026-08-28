package com.guanseq.quality.api;

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

import com.guanseq.quality.internal.NonconformanceApplicationService;

@RestController
@RequestMapping(path = "/api/v1/quality/nonconformances", produces = MediaType.APPLICATION_JSON_VALUE)
public class NonconformanceController {
	private final NonconformanceApplicationService service;
	NonconformanceController(NonconformanceApplicationService service) { this.service = service; }

	@GetMapping
	NonconformancePage list(Principal principal,
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String queue,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "ALL") String severity,
			@RequestParam(defaultValue = "ALL") String sourceType,
			@RequestParam(defaultValue = "false") boolean overdue,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(principal.getName(), query, queue, status, severity, sourceType, overdue, page, size);
	}

	@GetMapping("/{id}")
	NonconformanceRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(path = "/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	NonconformanceRecord act(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody NonconformanceActionRequest request) {
		return service.act(principal.getName(), id, request);
	}
}
