package com.guanseq.production.api;

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

import com.guanseq.production.internal.ProductionWorkReportApplicationService;

@RestController
@RequestMapping(path = "/api/v1/production/work-reports", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductionWorkReportController {
	private final ProductionWorkReportApplicationService service;
	ProductionWorkReportController(ProductionWorkReportApplicationService service) { this.service = service; }

	@GetMapping
	ProductionWorkReportPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/{id}")
	ProductionWorkReportRecord get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ProductionWorkReportRecord create(Principal principal, @Valid @RequestBody ProductionWorkReportRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PostMapping(path = "/{id}/settle", consumes = MediaType.APPLICATION_JSON_VALUE)
	ProductionWorkReportRecord settle(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody ProductionWorkReportRecord.SettleRequest request) {
		return service.settle(principal.getName(), id, request);
	}
}
