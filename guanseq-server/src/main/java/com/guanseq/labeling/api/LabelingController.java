package com.guanseq.labeling.api;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guanseq.labeling.internal.LabelingApplicationService;

@RestController
@RequestMapping(path = "/api/v1/labeling", produces = MediaType.APPLICATION_JSON_VALUE)
public class LabelingController {
	private final LabelingApplicationService service;

	LabelingController(LabelingApplicationService service) { this.service = service; }

	@GetMapping("/reference-data")
	LabelReferenceData referenceData(Principal principal) { return service.referenceData(principal.getName()); }

	@GetMapping("/print-requests")
	LabelPrintRequestPage list(Principal principal, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "ALL") String objectType, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(principal.getName(), query, objectType, page, size);
	}

	@PostMapping(path = "/print-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
	LabelPrintRequestRecord prepare(Principal principal, @Valid @RequestBody LabelPrintRequestRecord.PrepareRequest request) {
		return service.prepare(principal.getName(), request);
	}
}

