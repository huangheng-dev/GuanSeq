package com.guanseq.procurement.api;

import java.security.Principal;
import java.util.UUID;

import com.guanseq.procurement.internal.PurchaseReturnApplicationService;
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
@RequestMapping(path = "/api/v1/procurement", produces = MediaType.APPLICATION_JSON_VALUE)
public class PurchaseReturnController {
	private final PurchaseReturnApplicationService service;
	PurchaseReturnController(PurchaseReturnApplicationService service) { this.service = service; }
	@GetMapping("/returns") PurchaseReturnPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) { return service.list(principal.getName(), query, status, page, size); }
	@GetMapping("/returns/{id}") PurchaseReturnRecord get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }
	@GetMapping("/return-reference-data") PurchaseReturnReferenceData references(Principal principal) { return service.referenceData(principal.getName()); }
	@PostMapping(path = "/returns", consumes = MediaType.APPLICATION_JSON_VALUE)
	PurchaseReturnRecord create(Principal principal, @Valid @RequestBody PurchaseReturnRecord.CreateRequest request) { return service.create(principal.getName(), request); }
	@PostMapping(path = "/returns/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	PurchaseReturnRecord act(Principal principal, @PathVariable UUID id, @Valid @RequestBody PurchaseReturnRecord.ActionRequest request) { return service.act(principal.getName(), id, request); }
}
