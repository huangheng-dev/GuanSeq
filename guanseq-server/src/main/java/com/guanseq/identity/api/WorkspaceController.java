package com.guanseq.identity.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.identity.internal.WorkspaceApplicationService;

@RestController
@RequestMapping(path = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkspaceController {

	private final WorkspaceApplicationService workspaceApplicationService;

	WorkspaceController(WorkspaceApplicationService workspaceApplicationService) {
		this.workspaceApplicationService = workspaceApplicationService;
	}

	@GetMapping("/workspaces")
	WorkspaceSession getWorkspaces(Principal principal) {
		return workspaceApplicationService.getSession(principal.getName());
	}

	@PutMapping(path = "/current-workspace", consumes = MediaType.APPLICATION_JSON_VALUE)
	WorkspaceSession switchWorkspace(Principal principal, @Valid @RequestBody SwitchWorkspaceRequest request) {
		return workspaceApplicationService.switchWorkspace(
				principal.getName(),
				request.workspaceId(),
				request.expectedVersion());
	}

	public record SwitchWorkspaceRequest(
			@NotNull UUID workspaceId,
			@Min(0) long expectedVersion) {
	}
}
