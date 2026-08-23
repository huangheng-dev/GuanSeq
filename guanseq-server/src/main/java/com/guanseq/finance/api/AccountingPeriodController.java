package com.guanseq.finance.api;

import java.security.Principal;
import java.util.List;
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

import com.guanseq.finance.internal.AccountingPeriodApplicationService;

@RestController
@RequestMapping(path = "/api/v1/finance/accounting-periods", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountingPeriodController {

	private final AccountingPeriodApplicationService service;

	AccountingPeriodController(AccountingPeriodApplicationService service) {
		this.service = service;
	}

	@GetMapping
	List<AccountingPeriodRecord> list(Principal principal,
			@RequestParam(required = false) Integer year) {
		return service.list(principal.getName(), year);
	}

	@GetMapping(path = "/{id}")
	AccountingPeriodRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	AccountingPeriodRecord create(Principal principal,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody AccountingPeriodRecord.CreateRequest request) {
		return service.create(principal.getName(), requestId, request);
	}

	@PostMapping(path = "/{id}/close")
	AccountingPeriodRecord close(Principal principal, @PathVariable UUID id,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return service.close(principal.getName(), requestId, id);
	}

	@PostMapping(path = "/{id}/reopen", consumes = MediaType.APPLICATION_JSON_VALUE)
	AccountingPeriodRecord reopen(Principal principal, @PathVariable UUID id,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody AccountingPeriodRecord.ReopenRequest request) {
		return service.reopen(principal.getName(), requestId, id, request);
	}
}
