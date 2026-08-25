package com.guanseq.equipment.api;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.equipment.internal.EquipmentSparePartApplicationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/api/v1/equipment/spare-parts", produces = MediaType.APPLICATION_JSON_VALUE)
public class EquipmentSparePartController {
	private final EquipmentSparePartApplicationService service;

	EquipmentSparePartController(EquipmentSparePartApplicationService service) { this.service = service; }

	@GetMapping
	EquipmentSparePartPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, page, size);
	}

	@GetMapping("/references")
	EquipmentSparePartReferenceData references(Principal principal) { return service.references(principal.getName()); }

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	EquipmentSparePartRecord create(Principal principal, @Valid @RequestBody EquipmentSparePartRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}
}
