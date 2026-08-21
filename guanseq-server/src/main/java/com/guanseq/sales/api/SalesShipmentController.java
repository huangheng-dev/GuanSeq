package com.guanseq.sales.api;

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

import com.guanseq.sales.internal.SalesShipmentApplicationService;

@RestController
@RequestMapping(path = "/api/v1/sales", produces = MediaType.APPLICATION_JSON_VALUE)
public class SalesShipmentController {
	private final SalesShipmentApplicationService service;

	SalesShipmentController(SalesShipmentApplicationService service) {
		this.service = service;
	}

	@GetMapping("/shipments")
	SalesShipmentPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/shipments/{id}")
	SalesShipmentRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@GetMapping("/shipment-reference-data")
	SalesShipmentReferenceData referenceData(Principal principal) {
		return service.referenceData(principal.getName());
	}

	@PostMapping(path = "/shipments", consumes = MediaType.APPLICATION_JSON_VALUE)
	SalesShipmentRecord create(Principal principal, @Valid @RequestBody SalesShipmentRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}
}