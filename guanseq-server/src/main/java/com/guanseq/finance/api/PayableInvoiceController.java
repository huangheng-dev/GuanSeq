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

import com.guanseq.finance.internal.PayableApplicationService;

@RestController
@RequestMapping(path = "/api/v1/finance", produces = MediaType.APPLICATION_JSON_VALUE)
public class PayableInvoiceController {

	private final PayableApplicationService service;

	PayableInvoiceController(PayableApplicationService service) {
		this.service = service;
	}

	@GetMapping("/payable-invoices")
	PayableInvoicePage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/payable-invoices/{id}")
	PayableInvoiceRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@GetMapping("/payable-reference-data")
	PayableReferenceData referenceData(Principal principal) {
		return service.referenceData(principal.getName());
	}

	@PostMapping(path = "/payable-invoices", consumes = MediaType.APPLICATION_JSON_VALUE)
	PayableInvoiceRecord create(Principal principal,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody PayableInvoiceRecord.CreateRequest request) {
		return service.createInvoice(principal.getName(), requestId, request);
	}

	@PostMapping(path = "/payable-invoices/{id}/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
	PayableInvoiceRecord postPayment(Principal principal, @PathVariable UUID id,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody PayableInvoiceRecord.PaymentRequest request) {
		return service.postPayment(principal.getName(), id, requestId, request);
	}

	// ---- 红字发票与退款 / 反核销 ----

	@GetMapping("/payable-credit-notes")
	PayableCreditNotePage listCreditNotes(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return service.listCreditNotes(principal.getName(), query, page, size);
	}

	@GetMapping("/payable-credit-notes/{id}")
	PayableCreditNoteRecord getCreditNote(Principal principal, @PathVariable UUID id) {
		return service.getCreditNote(principal.getName(), id);
	}

	@PostMapping(path = "/payable-credit-notes", consumes = MediaType.APPLICATION_JSON_VALUE)
	PayableCreditNoteRecord createCreditNote(Principal principal,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody PayableCreditNoteRecord.CreateRequest request) {
		return service.createCreditNote(principal.getName(), requestId, request);
	}

	@PostMapping(path = "/payable-invoices/{id}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE)
	PayableInvoiceRecord postRefund(Principal principal, @PathVariable UUID id,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody PayableCreditNoteRecord.RefundRequest request) {
		return service.postRefund(principal.getName(), id, requestId, request);
	}

	@PostMapping(path = "/payables/payments/{id}/reverse", consumes = MediaType.APPLICATION_JSON_VALUE)
	PayableInvoiceRecord reversePayment(Principal principal, @PathVariable UUID id,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody PayableCreditNoteRecord.ReverseRequest request) {
		return service.reversePayment(principal.getName(), id, requestId, request);
	}
}
