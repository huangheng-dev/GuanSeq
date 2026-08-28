package com.guanseq.identity.api;

import java.security.Principal;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.identity.internal.WorkspaceRolePermissionApplicationService;

@RestController
@RequestMapping(path = "/api/v1/identity/role-permissions", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkspaceRolePermissionController {

	private final WorkspaceRolePermissionApplicationService service;

	WorkspaceRolePermissionController(WorkspaceRolePermissionApplicationService service) {
		this.service = service;
	}

	@GetMapping
	WorkspaceRolePermissionPage list(Principal principal) {
		return service.list(principal.getName());
	}
}
