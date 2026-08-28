package com.guanseq.sales.api;

import java.security.Principal;
import java.util.UUID;

import com.guanseq.sales.internal.SalesReturnApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/sales", produces = MediaType.APPLICATION_JSON_VALUE)
public class SalesReturnController {
	private final SalesReturnApplicationService service;

	SalesReturnController(SalesReturnApplicationService service) { this.service = service; }

	@GetMapping("/returns")
	SalesReturnPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/returns/{id}")
	SalesReturnRecord get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }

	@GetMapping("/return-reference-data")
	SalesReturnReferenceData referenceData(Principal principal) { return service.referenceData(principal.getName()); }

	@PostMapping(path = "/returns", consumes = MediaType.APPLICATION_JSON_VALUE)
	SalesReturnRecord create(Principal principal, @Valid @RequestBody SalesReturnRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PostMapping(path = "/returns/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	SalesReturnRecord act(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody SalesReturnRecord.ActionRequest request) {
		return service.act(principal.getName(), id, request);
	}
}
