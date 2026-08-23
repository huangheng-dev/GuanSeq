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

import com.guanseq.finance.internal.GrirAccrualApplicationService;

@RestController
@RequestMapping(path = "/api/v1/finance/grir-accruals", produces = MediaType.APPLICATION_JSON_VALUE)
public class GrirAccrualController {

	private final GrirAccrualApplicationService service;

	GrirAccrualController(GrirAccrualApplicationService service) {
		this.service = service;
	}

	@GetMapping
	GrirAccrualPage list(Principal principal,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false, defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(principal.getName(), year, status, page, size);
	}

	@GetMapping(path = "/{id}")
	GrirAccrualRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@GetMapping(path = "/preview")
	GrirAccrualPreview preview(Principal principal,
			@RequestParam int year,
			@RequestParam int period) {
		return service.preview(principal.getName(), year, period);
	}

	@PostMapping(path = "/run", consumes = MediaType.APPLICATION_JSON_VALUE)
	GrirAccrualRecord run(Principal principal,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody GrirAccrualRecord.RunRequest request) {
		return service.run(principal.getName(), requestId, request);
	}

	@PostMapping(path = "/{id}/reverse", consumes = MediaType.APPLICATION_JSON_VALUE)
	GrirAccrualRecord reverse(Principal principal, @PathVariable UUID id,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody GrirAccrualRecord.ReverseRequest request) {
		return service.reverse(principal.getName(), id, requestId, request);
	}
}
