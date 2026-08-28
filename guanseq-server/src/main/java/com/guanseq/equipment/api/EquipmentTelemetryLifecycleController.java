package com.guanseq.equipment.api;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.equipment.internal.EquipmentTelemetryLifecycleApplicationService;

@RestController
public class EquipmentTelemetryLifecycleController {

	private final EquipmentTelemetryLifecycleApplicationService service;

	EquipmentTelemetryLifecycleController(EquipmentTelemetryLifecycleApplicationService service) {
		this.service = service;
	}

	@GetMapping(path = "/api/v1/equipment/telemetry-samples", produces = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetrySamplePage history(Principal principal,
			@RequestParam UUID connectionId,
			@RequestParam(required = false) String pointCode,
			@RequestParam(required = false) String quality,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.history(principal.getName(), connectionId, pointCode, quality, from, to, page, size);
	}

	@GetMapping(path = "/api/v1/equipment/telemetry-retention-policy",
			produces = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryRetentionRecord policy(Principal principal) {
		return service.getPolicy(principal.getName());
	}

	@PutMapping(path = "/api/v1/equipment/telemetry-retention-policy",
			consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryRetentionRecord updatePolicy(Principal principal,
			@Valid @RequestBody EquipmentTelemetryRetentionRecord.UpdateRequest request) {
		return service.updatePolicy(principal.getName(), request);
	}

	@PostMapping(path = "/api/v1/equipment/telemetry-retention-policy/cleanup",
			consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryRetentionRecord.CleanupResult cleanup(Principal principal,
			@Valid @RequestBody EquipmentTelemetryRetentionRecord.CleanupRequest request) {
		return service.cleanup(principal.getName(), request);
	}

	@PostMapping(path = "/api/v1/equipment/telemetry-retention-policy/automation/run-now",
			consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryRetentionRecord.AutomationActionResult runAutomationNow(Principal principal,
			@Valid @RequestBody EquipmentTelemetryRetentionRecord.RunNowRequest request) {
		return service.runAutomationNow(principal.getName(), request);
	}

	@PostMapping(path = "/api/v1/equipment/telemetry-retention-runs/{id}/acknowledge",
			consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	EquipmentTelemetryRetentionRecord.AutomationActionResult acknowledgeAutomationFailure(Principal principal,
			@PathVariable UUID id,
			@Valid @RequestBody EquipmentTelemetryRetentionRecord.AcknowledgeFailureRequest request) {
		return service.acknowledgeAutomationFailure(principal.getName(), id, request);
	}
}
