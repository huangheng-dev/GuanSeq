package com.guanseq.equipment.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.equipment.internal.EquipmentWorkOrderApplicationService;
import com.guanseq.equipment.internal.EquipmentMaintenanceCostApplicationService;

@RestController
@RequestMapping(path = "/api/v1/equipment/work-orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class EquipmentWorkOrderController {

	private final EquipmentWorkOrderApplicationService service;
	private final EquipmentMaintenanceCostApplicationService costService;

	EquipmentWorkOrderController(EquipmentWorkOrderApplicationService service,
			EquipmentMaintenanceCostApplicationService costService) { this.service = service; this.costService = costService; }

	@GetMapping
	EquipmentWorkOrderPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String type,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, type, status, page, size);
	}

	@GetMapping("/{id}")
	EquipmentWorkOrderRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	EquipmentWorkOrderRecord create(Principal principal,
			@Valid @RequestBody EquipmentWorkOrderRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PostMapping(path = "/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentWorkOrderRecord act(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentWorkOrderRecord.ActionRequest request) {
		return service.act(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/spare-issues", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentMaintenanceCostRecord.MutationResult issueSpare(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentMaintenanceCostRecord.IssueRequest request) {
		return costService.issue(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/spare-returns", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentMaintenanceCostRecord.MutationResult returnSpare(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentMaintenanceCostRecord.ReturnRequest request) {
		return costService.returnSpare(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/labor-entries", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentMaintenanceCostRecord.MutationResult recordLabor(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentMaintenanceCostRecord.LaborEntryRequest request) {
		return costService.recordLabor(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/labor-entries/{entryId}/reversals", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentMaintenanceCostRecord.MutationResult reverseLabor(Principal principal, @PathVariable UUID id,
			@PathVariable UUID entryId, @Valid @RequestBody EquipmentMaintenanceCostRecord.LaborReversalRequest request) {
		return costService.reverseLabor(principal.getName(), id, entryId, request);
	}
}
