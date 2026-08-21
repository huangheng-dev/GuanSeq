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

import com.guanseq.product.internal.BomApplicationService;

@RestController
@RequestMapping(path = "/api/v1/product", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductBomController {

	private final BomApplicationService service;

	ProductBomController(BomApplicationService service) {
		this.service = service;
	}

	@GetMapping("/boms")
	BomPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/boms/{id}")
	BomRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(path = "/boms", consumes = MediaType.APPLICATION_JSON_VALUE)
	BomRecord create(Principal principal, @Valid @RequestBody BomRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PutMapping(path = "/boms/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	BomRecord update(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody BomRecord.UpdateRequest request) {
		return service.update(principal.getName(), id, request);
	}

	@PostMapping(path = "/boms/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	BomRecord act(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody BomRecord.ActionRequest request) {
		return service.act(principal.getName(), id, request);
	}

	@GetMapping("/bom-reference-data")
	BomReferenceData referenceData(Principal principal) {
		return service.referenceData(principal.getName());
	}
}
