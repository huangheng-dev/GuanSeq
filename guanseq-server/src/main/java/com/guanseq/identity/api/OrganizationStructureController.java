package com.guanseq.identity.api;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.identity.internal.OrganizationStructureApplicationService;

@RestController
@RequestMapping(path = "/api/v1/identity/organization-structure", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrganizationStructureController {
	private final OrganizationStructureApplicationService service;

	OrganizationStructureController(OrganizationStructureApplicationService service) { this.service = service; }

	@GetMapping
	OrganizationStructurePage get(Principal principal) { return service.get(principal.getName()); }

	@PostMapping(path = "/units", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	OrganizationStructurePage createSite(Principal principal, @Valid @RequestBody OrganizationStructureCommand.CreateSite request) {
		return service.createSite(principal.getName(), request);
	}

	@PutMapping(path = "/units/{unitId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	OrganizationStructurePage updateUnit(Principal principal, @PathVariable UUID unitId,
			@Valid @RequestBody OrganizationStructureCommand.UpdateUnit request) {
		return service.updateUnit(principal.getName(), unitId, request);
	}

	@PostMapping(path = "/units/{unitId}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	OrganizationStructurePage actUnit(Principal principal, @PathVariable UUID unitId,
			@Valid @RequestBody OrganizationStructureCommand.UnitAction request) {
		return service.actUnit(principal.getName(), unitId, request);
	}

	@PutMapping(path = "/workspace", consumes = MediaType.APPLICATION_JSON_VALUE)
	OrganizationStructurePage updateWorkspace(Principal principal,
			@Valid @RequestBody OrganizationStructureCommand.UpdateWorkspace request) {
		return service.updateWorkspace(principal.getName(), request);
	}

	@PutMapping(path = "/members/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	OrganizationStructurePage assignMember(Principal principal, @PathVariable UUID userId,
			@Valid @RequestBody OrganizationStructureCommand.AssignMember request) {
		return service.assignMember(principal.getName(), userId, request);
	}
}
