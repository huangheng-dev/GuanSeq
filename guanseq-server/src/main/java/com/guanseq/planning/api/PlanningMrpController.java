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

import com.guanseq.planning.internal.MrpRunApplicationService;

@RestController
@RequestMapping(path = "/api/v1/planning/mrp-runs", produces = MediaType.APPLICATION_JSON_VALUE)
public class PlanningMrpController {

	private final MrpRunApplicationService service;

	PlanningMrpController(MrpRunApplicationService service) {
		this.service = service;
	}

	@GetMapping
	MrpRunPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/{id}")
	MrpRunRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	MrpRunRecord create(Principal principal, @Valid @RequestBody MrpRunRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}
}
