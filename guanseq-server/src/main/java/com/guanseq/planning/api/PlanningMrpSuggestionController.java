package com.guanseq.planning.api;

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

import com.guanseq.planning.internal.MrpSuggestionApplicationService;

@RestController
@RequestMapping(path = "/api/v1/planning/mrp-suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
public class PlanningMrpSuggestionController {
	private final MrpSuggestionApplicationService service;

	PlanningMrpSuggestionController(MrpSuggestionApplicationService service) { this.service = service; }

	@GetMapping
	MrpSuggestionPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "ALL") String type,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, type, page, size);
	}

	@GetMapping("/{id}")
	MrpSuggestionRecord get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }

	@PostMapping(path = "/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	MrpSuggestionRecord action(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody MrpSuggestionRecord.ActionRequest request) {
		return service.action(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/convert", consumes = MediaType.APPLICATION_JSON_VALUE)
	MrpSuggestionRecord convert(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody MrpSuggestionRecord.ConvertRequest request) {
		return service.convert(principal.getName(), id, request);
	}
}
