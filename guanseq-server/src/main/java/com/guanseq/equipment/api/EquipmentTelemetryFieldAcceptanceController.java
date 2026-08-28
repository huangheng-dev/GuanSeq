package com.guanseq.equipment.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.equipment.internal.EquipmentTelemetryFieldAcceptanceApplicationService;

@RestController
@RequestMapping(path = "/api/v1/equipment/telemetry-field-acceptances", produces = MediaType.APPLICATION_JSON_VALUE)
public class EquipmentTelemetryFieldAcceptanceController {

	private final EquipmentTelemetryFieldAcceptanceApplicationService service;

	EquipmentTelemetryFieldAcceptanceController(EquipmentTelemetryFieldAcceptanceApplicationService service) {
		this.service = service;
	}

	@GetMapping("/{connectionId}")
	EquipmentTelemetryFieldAcceptanceRecord.Context get(Principal principal, @PathVariable UUID connectionId) {
		return service.get(principal.getName(), connectionId);
	}

	@PutMapping(path = "/{connectionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryFieldAcceptanceRecord.Context save(Principal principal, @PathVariable UUID connectionId,
			@Valid @RequestBody EquipmentTelemetryFieldAcceptanceRecord.SaveRequest request) {
		return service.save(principal.getName(), connectionId, request);
	}

	@PostMapping(path = "/{connectionId}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryFieldAcceptanceRecord.Context act(Principal principal, @PathVariable UUID connectionId,
			@Valid @RequestBody EquipmentTelemetryFieldAcceptanceRecord.ActionRequest request) {
		return service.act(principal.getName(), connectionId, request);
	}
}
