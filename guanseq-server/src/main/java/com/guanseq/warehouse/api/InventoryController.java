package com.guanseq.warehouse.api;

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

import com.guanseq.warehouse.internal.InventoryApplicationService;

@RestController
@RequestMapping(path = "/api/v1/warehouse", produces = MediaType.APPLICATION_JSON_VALUE)
public class InventoryController {
	private final InventoryApplicationService service;

	InventoryController(InventoryApplicationService service) { this.service = service; }

	@GetMapping("/inventory-balances")
	InventoryPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String qualityStatus,
			@RequestParam(defaultValue = "ALL") String warehouseCode,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, qualityStatus, warehouseCode, page, size);
	}

	@GetMapping("/inventory-balances/{id}")
	InventoryRecord get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }

	@PostMapping(path = "/inventory-balances/{id}/movements", consumes = MediaType.APPLICATION_JSON_VALUE)
	InventoryRecord postMovement(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody InventoryRecord.MovementRequest request) {
		return service.postMovement(principal.getName(), id, request);
	}

	@GetMapping("/inventory-reference-data")
	InventoryReferenceData referenceData(Principal principal) { return service.referenceData(principal.getName()); }
}
