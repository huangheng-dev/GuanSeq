package com.guanseq.procurement.api;

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

import com.guanseq.procurement.internal.PurchaseReceiptApplicationService;

@RestController
@RequestMapping(path = "/api/v1/procurement", produces = MediaType.APPLICATION_JSON_VALUE)
public class PurchaseReceiptController {
	private final PurchaseReceiptApplicationService service;
	PurchaseReceiptController(PurchaseReceiptApplicationService service) { this.service = service; }

	@GetMapping("/receipts")
	PurchaseReceiptPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/receipts/{id}")
	PurchaseReceiptRecord get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }

	@GetMapping("/receipt-reference-data")
	PurchaseReceiptReferenceData referenceData(Principal principal) { return service.referenceData(principal.getName()); }

	@PostMapping(path = "/receipts", consumes = MediaType.APPLICATION_JSON_VALUE)
	PurchaseReceiptRecord create(Principal principal, @Valid @RequestBody PurchaseReceiptRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}
}
