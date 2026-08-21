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

import com.guanseq.production.internal.MaterialIssueApplicationService;

@RestController
@RequestMapping(path = "/api/v1/production", produces = MediaType.APPLICATION_JSON_VALUE)
public class MaterialIssueController {
	private final MaterialIssueApplicationService service;

	MaterialIssueController(MaterialIssueApplicationService service) { this.service = service; }

	@GetMapping("/material-issues")
	MaterialIssuePage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/material-issues/{id}")
	MaterialIssueRecord get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }

	@GetMapping("/material-issue-reference-data")
	MaterialIssueReferenceData referenceData(Principal principal) { return service.referenceData(principal.getName()); }

	@PostMapping(path = "/material-issues", consumes = MediaType.APPLICATION_JSON_VALUE)
	MaterialIssueRecord create(Principal principal, @Valid @RequestBody MaterialIssueRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PostMapping(path = "/material-issues/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	MaterialIssueRecord action(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody MaterialIssueRecord.ActionRequest request) {
		return service.action(principal.getName(), id, request);
	}

	@PostMapping(path = "/material-issues/{id}/returns", consumes = MediaType.APPLICATION_JSON_VALUE)
	MaterialIssueRecord createReturn(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody MaterialIssueRecord.ReturnRequest request) {
		return service.createReturn(principal.getName(), id, request);
	}
}
