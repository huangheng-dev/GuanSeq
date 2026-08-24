package com.guanseq.procurement.api;

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

import com.guanseq.procurement.internal.SupplierApplicationService;

@RestController
@RequestMapping(path = "/api/v1/procurement/suppliers", produces = MediaType.APPLICATION_JSON_VALUE)
public class SupplierController {
	private final SupplierApplicationService service;
	SupplierController(SupplierApplicationService service) { this.service = service; }

	@GetMapping
	SupplierPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	SupplierRecord create(Principal principal, @Valid @RequestBody SupplierRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	SupplierRecord update(Principal principal, @PathVariable UUID id, @Valid @RequestBody SupplierRecord.UpdateRequest request) {
		return service.update(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	SupplierRecord action(Principal principal, @PathVariable UUID id, @Valid @RequestBody SupplierRecord.StatusRequest request) {
		return service.changeStatus(principal.getName(), id, request.status(), request.expectedVersion());
	}
}
