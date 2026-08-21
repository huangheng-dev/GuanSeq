package com.guanseq.quality.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.quality.internal.FinalInspectionApplicationService;

@RestController
@RequestMapping(path = "/api/v1/quality/incoming-inspections", produces = MediaType.APPLICATION_JSON_VALUE)
public class IncomingInspectionController {
	private final FinalInspectionApplicationService service;
	IncomingInspectionController(FinalInspectionApplicationService service) { this.service = service; }

	@GetMapping
	IncomingInspectionPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.listIncoming(principal.getName(), query, status, page, size);
	}

	@GetMapping("/{id}")
	IncomingInspectionRecord get(Principal principal, @PathVariable UUID id) {
		return service.getIncoming(principal.getName(), id);
	}

	@PostMapping(path = "/{id}/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
	IncomingInspectionRecord complete(Principal principal, @PathVariable UUID id,
			@Valid @RequestBody IncomingInspectionRecord.CompleteRequest request) {
		return service.completeIncoming(principal.getName(), id, request);
	}
}
