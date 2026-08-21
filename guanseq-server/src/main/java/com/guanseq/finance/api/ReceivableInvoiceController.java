package com.guanseq.finance.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.finance.internal.ReceivableApplicationService;

@RestController
@RequestMapping(path = "/api/v1/finance", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReceivableInvoiceController {

	private final ReceivableApplicationService service;

	ReceivableInvoiceController(ReceivableApplicationService service) {
		this.service = service;
	}

	@GetMapping("/receivable-invoices")
	ReceivableInvoicePage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/receivable-invoices/{id}")
	ReceivableInvoiceRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@GetMapping("/receivable-reference-data")
	ReceivableReferenceData referenceData(Principal principal) {
		return service.referenceData(principal.getName());
	}

	@PostMapping(path = "/receivable-invoices", consumes = MediaType.APPLICATION_JSON_VALUE)
	ReceivableInvoiceRecord create(Principal principal,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody ReceivableInvoiceRecord.CreateRequest request) {
		return service.createInvoice(principal.getName(), requestId, request);
	}

	@PostMapping(path = "/receivable-invoices/{id}/receipts", consumes = MediaType.APPLICATION_JSON_VALUE)
	ReceivableInvoiceRecord postReceipt(Principal principal, @PathVariable UUID id,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody ReceivableInvoiceRecord.ReceiptRequest request) {
		return service.postReceipt(principal.getName(), id, requestId, request);
	}
}
