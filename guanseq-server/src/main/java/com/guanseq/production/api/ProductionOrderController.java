package com.guanseq.production.api;

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

import com.guanseq.production.internal.ProductionOrderApplicationService;

@RestController
@RequestMapping(path = "/api/v1/production", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductionOrderController {
	private final ProductionOrderApplicationService service;
	ProductionOrderController(ProductionOrderApplicationService service) { this.service = service; }

	@GetMapping("/orders")
	ProductionOrderPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/orders/{id}")
	ProductionOrderRecord get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }

	@GetMapping("/order-reference-data")
	ProductionOrderReferenceData referenceData(Principal principal) { return service.referenceData(principal.getName()); }

	@PostMapping(path = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
	ProductionOrderRecord create(Principal principal, @Valid @RequestBody ProductionOrderRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PutMapping(path = "/orders/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	ProductionOrderRecord update(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody ProductionOrderRecord.UpdateRequest request) {
		return service.update(principal.getName(), id, request);
	}

	@PostMapping(path = "/orders/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	ProductionOrderRecord action(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody ProductionOrderRecord.ActionRequest request) {
		return service.action(principal.getName(), id, request);
	}
}
