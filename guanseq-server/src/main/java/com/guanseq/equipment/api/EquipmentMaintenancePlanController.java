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

import com.guanseq.equipment.internal.EquipmentMaintenancePlanApplicationService;

@RestController
@RequestMapping(path = "/api/v1/equipment/maintenance-plans", produces = MediaType.APPLICATION_JSON_VALUE)
public class EquipmentMaintenancePlanController {

	private final EquipmentMaintenancePlanApplicationService service;

	EquipmentMaintenancePlanController(EquipmentMaintenancePlanApplicationService service) { this.service = service; }

	@GetMapping
	EquipmentMaintenancePlanPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "200") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/{id}")
	EquipmentMaintenancePlanRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	EquipmentMaintenancePlanRecord create(Principal principal,
			@Valid @RequestBody EquipmentMaintenancePlanRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PostMapping(path = "/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentMaintenancePlanRecord act(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentMaintenancePlanRecord.ActionRequest request) {
		return service.act(principal.getName(), id, request);
	}

	@PostMapping(path = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentMaintenanceGenerationRecord generate(Principal principal,
			@Valid @RequestBody EquipmentMaintenancePlanRecord.GenerateRequest request) {
		return service.generate(principal.getName(), request);
	}
}
