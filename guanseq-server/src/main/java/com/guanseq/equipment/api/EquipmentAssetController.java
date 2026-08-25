package com.guanseq.equipment.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.equipment.internal.EquipmentAssetApplicationService;

@RestController
@RequestMapping(path = "/api/v1/equipment/assets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EquipmentAssetController {

	private final EquipmentAssetApplicationService service;

	EquipmentAssetController(EquipmentAssetApplicationService service) { this.service = service; }

	@GetMapping
	EquipmentAssetPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "ALL") String category,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, category, page, size);
	}

	@GetMapping("/{id}")
	EquipmentAssetRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	EquipmentAssetRecord create(Principal principal, @Valid @RequestBody EquipmentAssetRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentAssetRecord update(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentAssetRecord.UpdateRequest request) {
		return service.update(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentAssetRecord act(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentAssetRecord.ActionRequest request) {
		return service.act(principal.getName(), id, request);
	}
}
