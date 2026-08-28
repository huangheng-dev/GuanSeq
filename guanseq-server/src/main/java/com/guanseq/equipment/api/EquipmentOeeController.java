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

import com.guanseq.equipment.internal.EquipmentOeeApplicationService;

@RestController
@RequestMapping(path = "/api/v1/equipment/oee-records", produces = MediaType.APPLICATION_JSON_VALUE)
public class EquipmentOeeController {

	private final EquipmentOeeApplicationService service;

	EquipmentOeeController(EquipmentOeeApplicationService service) { this.service = service; }

	@GetMapping
	EquipmentOeePage list(Principal principal,
			@RequestParam(defaultValue = "") String query,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@GetMapping("/{id}")
	EquipmentOeeRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	EquipmentOeeRecord create(Principal principal, @Valid @RequestBody EquipmentOeeRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PostMapping(path = "/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentOeeRecord act(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentOeeRecord.ActionRequest request) {
		return service.act(principal.getName(), id, request);
	}
}
