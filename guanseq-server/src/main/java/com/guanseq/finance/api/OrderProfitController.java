package com.guanseq.finance.api;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.finance.internal.OrderProfitApplicationService;

@RestController
@RequestMapping(path = "/api/v1/finance", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderProfitController {
	private final OrderProfitApplicationService service;

	OrderProfitController(OrderProfitApplicationService service) { this.service = service; }

	@GetMapping("/order-profits")
	OrderProfitPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String costStatus, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, costStatus, page, size);
	}

	@GetMapping("/order-profit-reference-data")
	OrderProfitReferenceData referenceData(Principal principal) {
		return service.referenceData(principal.getName());
	}

	@GetMapping("/order-profits/{id}")
	OrderProfitRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(path = "/order-profits/{salesOrderId}/settle")
	OrderProfitRecord settle(Principal principal, @PathVariable UUID salesOrderId,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return service.settle(principal.getName(), salesOrderId, requestId);
	}
}
