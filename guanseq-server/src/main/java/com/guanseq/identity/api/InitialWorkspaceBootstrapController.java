package com.guanseq.identity.api;

import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.identity.internal.InitialWorkspaceBootstrapApplicationService;

@RestController
@RequestMapping(path = "/api/v1/bootstrap", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(name = "guanseq.bootstrap.enabled", havingValue = "true")
class InitialWorkspaceBootstrapController {

	private static final String TOKEN_HEADER = "X-GuanSeq-Bootstrap-Token";

	private final InitialWorkspaceBootstrapApplicationService bootstrapService;

	InitialWorkspaceBootstrapController(InitialWorkspaceBootstrapApplicationService bootstrapService) {
		this.bootstrapService = bootstrapService;
	}

	@PostMapping(path = "/initial-workspace", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<InitialWorkspaceBootstrap.Response> bootstrap(
			@RequestHeader(name = TOKEN_HEADER, required = false) String bootstrapToken,
			@Valid @RequestBody InitialWorkspaceBootstrap.Request request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(bootstrapService.bootstrap(bootstrapToken, request, MDC.get("requestId")));
	}
}
