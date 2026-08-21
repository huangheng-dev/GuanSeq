package com.guanseq.product.api;

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

import com.guanseq.product.internal.RoutingApplicationService;

@RestController
@RequestMapping(path = "/api/v1/product", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductRoutingController {

	private final RoutingApplicationService service;

	ProductRoutingController(RoutingApplicationService service) {
		this.service = service;
	}

	@GetMapping("/routings")
	RoutingPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/routings/{id}")
	RoutingRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(path = "/routings", consumes = MediaType.APPLICATION_JSON_VALUE)
	RoutingRecord create(Principal principal, @Valid @RequestBody RoutingRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PutMapping(path = "/routings/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	RoutingRecord update(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody RoutingRecord.UpdateRequest request) {
		return service.update(principal.getName(), id, request);
	}

	@PostMapping(path = "/routings/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	RoutingRecord act(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody RoutingRecord.ActionRequest request) {
		return service.act(principal.getName(), id, request);
	}

	@GetMapping("/routing-reference-data")
	RoutingReferenceData referenceData(Principal principal) {
		return service.referenceData(principal.getName());
	}
}
