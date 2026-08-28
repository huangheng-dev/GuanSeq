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

import com.guanseq.equipment.internal.EquipmentTelemetryApplicationService;

@RestController
@RequestMapping(path = "/api/v1/equipment/telemetry-connections", produces = MediaType.APPLICATION_JSON_VALUE)
public class EquipmentTelemetryConnectionController {

	private final EquipmentTelemetryApplicationService service;

	EquipmentTelemetryConnectionController(EquipmentTelemetryApplicationService service) {
		this.service = service;
	}

	@GetMapping
	EquipmentTelemetryConnectionPage list(Principal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(principal.getName(), page, size);
	}

	@GetMapping("/{id}")
	EquipmentTelemetryConnectionRecord get(Principal principal, @PathVariable UUID id) {
		return service.get(principal.getName(), id);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	EquipmentTelemetryConnectionRecord create(Principal principal,
			@Valid @RequestBody EquipmentTelemetryConnectionRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PostMapping(path = "/{id}/test", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryConnectionRecord.ActionResult test(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentTelemetryConnectionRecord.ActionRequest request) {
		return service.test(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/activate", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryConnectionRecord activate(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentTelemetryConnectionRecord.ActionRequest request) {
		return service.activate(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/pause", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryConnectionRecord pause(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentTelemetryConnectionRecord.ActionRequest request) {
		return service.pause(principal.getName(), id, request);
	}

	@PostMapping(path = "/{id}/poll", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryConnectionRecord.ActionResult poll(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentTelemetryConnectionRecord.ActionRequest request) {
		return service.poll(principal.getName(), id, request);
	}
}
