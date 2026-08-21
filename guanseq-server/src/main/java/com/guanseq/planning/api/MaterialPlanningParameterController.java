package com.guanseq.planning.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.planning.internal.MaterialPlanningParameterApplicationService;

@RestController
@RequestMapping(path = "/api/v1/planning/material-parameters", produces = MediaType.APPLICATION_JSON_VALUE)
public class MaterialPlanningParameterController {
	private final MaterialPlanningParameterApplicationService service;
	MaterialPlanningParameterController(MaterialPlanningParameterApplicationService service) { this.service = service; }

	@GetMapping
	MaterialPlanningParameterPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, page, size);
	}

	@PutMapping(path = "/{materialId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	MaterialPlanningParameterRecord update(Principal principal, @PathVariable UUID materialId,
			@Valid @RequestBody MaterialPlanningParameterRecord.UpdateRequest request) {
		return service.update(principal.getName(), materialId, request);
	}
}
