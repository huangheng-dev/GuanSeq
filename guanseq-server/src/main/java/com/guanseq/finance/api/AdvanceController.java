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

import com.guanseq.finance.internal.AdvanceApplicationService;

@RestController
@RequestMapping(path = "/api/v1/finance/advances", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdvanceController {

	private final AdvanceApplicationService service;

	AdvanceController(AdvanceApplicationService service) {
		this.service = service;
	}

	@GetMapping
	AdvancePage list(Principal principal,
			@RequestParam(required = false) String type,
			@RequestParam(required = false, defaultValue = "ALL") String status,
			@RequestParam(required = false) String partyId,
			@RequestParam(required = false, defaultValue = "") String query,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(principal.getName(), type, status, partyId, query, page, size);
	}

	@GetMapping(path = "/{id}")
	AdvanceRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	AdvanceRecord register(Principal principal,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody AdvanceRecord.CreateRequest request) {
		return service.register(principal.getName(), requestId, request);
	}

	@PostMapping(path = "/{id}/refund", consumes = MediaType.APPLICATION_JSON_VALUE)
	AdvanceRecord refund(Principal principal, @PathVariable UUID id,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody AdvanceRecord.RefundRequest request) {
		return service.refund(principal.getName(), id, requestId, request);
	}
}
