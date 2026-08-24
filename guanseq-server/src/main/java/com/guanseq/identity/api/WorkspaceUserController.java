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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.identity.internal.WorkspaceUserApplicationService;

@RestController
@RequestMapping(path = "/api/v1/identity/workspace-users", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkspaceUserController {

	private final WorkspaceUserApplicationService service;

	WorkspaceUserController(WorkspaceUserApplicationService service) {
		this.service = service;
	}

	@GetMapping
	WorkspaceUserPage list(
			Principal principal,
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, status, page, size);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	WorkspaceUserRecord create(Principal principal, @Valid @RequestBody WorkspaceUserRecord.CreateRequest request) {
		return service.create(principal.getName(), request);
	}

	@PutMapping(path = "/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	WorkspaceUserRecord update(
			Principal principal,
			@PathVariable UUID userId,
			@Valid @RequestBody WorkspaceUserRecord.UpdateRequest request) {
		return service.update(principal.getName(), userId, request);
	}

	@PostMapping(path = "/{userId}/actions", consumes = MediaType.APPLICATION_JSON_VALUE)
	WorkspaceUserRecord act(
			Principal principal,
			@PathVariable UUID userId,
			@Valid @RequestBody WorkspaceUserRecord.ActionRequest request) {
		return service.act(principal.getName(), userId, request);
	}
}
