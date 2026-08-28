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

import com.guanseq.equipment.internal.EquipmentAlertApplicationService;

@RestController
@RequestMapping(path = "/api/v1/equipment", produces = MediaType.APPLICATION_JSON_VALUE)
public class EquipmentAlertController {

	private final EquipmentAlertApplicationService service;

	EquipmentAlertController(EquipmentAlertApplicationService service) { this.service = service; }

	@GetMapping("/alert-rules")
	EquipmentAlertRulePage listRules(Principal principal,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.listRules(principal.getName(), status, page, size);
	}

	@PostMapping(path = "/alert-rules", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	EquipmentAlertRuleRecord createRule(Principal principal,
			@Valid @RequestBody EquipmentAlertRuleRecord.CreateRequest request) {
		return service.createRule(principal.getName(), request);
	}

	@PostMapping(path = "/alert-rules/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentAlertRuleRecord actOnRule(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentAlertRuleRecord.ActionRequest request) {
		return service.actOnRule(principal.getName(), id, request);
	}

	@GetMapping("/alerts")
	EquipmentAlertPage listAlerts(Principal principal,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "ALL") String severity,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.listAlerts(principal.getName(), status, severity, page, size);
	}

	@GetMapping("/alerts/{id}")
	EquipmentAlertRecord getAlert(Principal principal, @PathVariable UUID id) {
		return service.getAlert(principal.getName(), id);
	}

	@PostMapping(path = "/alerts/{id}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	EquipmentAlertRecord actOnAlert(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody EquipmentAlertRecord.ActionRequest request) {
		return service.actOnAlert(principal.getName(), id, request);
	}
}
