package com.guanseq.planning.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.planning.internal.IndependentDemandApplicationService;

@RestController
@RequestMapping(path = "/api/v1/planning", produces = MediaType.APPLICATION_JSON_VALUE)
public class PlanningDemandController {

	private final IndependentDemandApplicationService service;

	PlanningDemandController(IndependentDemandApplicationService service) {
		this.service = service;
	}

	@GetMapping("/independent-demands")
	IndependentDemandPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "ALL") String sourceType,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, sourceType, page, size);
	}

	@GetMapping("/independent-demands/{id}")
	IndependentDemandRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@GetMapping("/demand-reference-data")
	PlanningDemandReferenceData referenceData(Principal principal) {
		return service.referenceData(principal.getName());
	}

	@GetMapping("/mrp-inputs")
	IndependentDemandPage mrpInputs(Principal principal, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size) {
		return service.list(principal.getName(), null, "ACTIVE", "ALL", page, size);
	}

	@PostMapping(path = "/independent-demands", consumes = MediaType.APPLICATION_JSON_VALUE)
	IndependentDemandRecord create(Principal principal, @Valid @RequestBody IndependentDemandRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PutMapping(path = "/independent-demands/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	IndependentDemandRecord update(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody IndependentDemandRecord.UpdateRequest request) {
		return service.update(principal.getName(), id, request);
	}

	@PostMapping(path = "/independent-demands/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	IndependentDemandRecord action(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody IndependentDemandRecord.ActionRequest request) {
		return service.action(principal.getName(), id, request);
	}
}
