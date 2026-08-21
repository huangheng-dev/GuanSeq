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

import com.guanseq.finance.internal.WorkCenterCostRateApplicationService;

@RestController
@RequestMapping(path = "/api/v1/finance/work-center-cost-rates", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkCenterCostRateController {

	private final WorkCenterCostRateApplicationService service;

	WorkCenterCostRateController(WorkCenterCostRateApplicationService service) {
		this.service = service;
	}

	@GetMapping
	WorkCenterCostRatePage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	WorkCenterCostRateRecord create(Principal principal,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody WorkCenterCostRateRecord.CreateRequest request) {
		return service.create(principal.getName(), requestId, request);
	}

	@PostMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
	WorkCenterCostRateRecord changeStatus(Principal principal, @PathVariable UUID id,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Valid @RequestBody WorkCenterCostRateRecord.StatusRequest request) {
		return service.changeStatus(principal.getName(), id, requestId, request);
	}
}
