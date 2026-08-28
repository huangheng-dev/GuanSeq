package com.guanseq.quality.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NonconformanceActionRequest(
		@NotBlank @Pattern(regexp = "REVIEW|DISPOSE|PLAN_ACTION|COMPLETE_ACTION|VERIFY|REOPEN") String action,
		@NotNull Long expectedVersion,
		@Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity,
		@Size(max = 1000) String immediateContainment,
		@Size(max = 1000) String reviewConclusion,
		Boolean capaRequired,
		@Pattern(regexp = "RETURN_TO_SUPPLIER|REWORK|SCRAP|CONCESSION|SORTING|OTHER") String dispositionType,
		@Size(max = 1000) String dispositionDecision,
		@Size(max = 1000) String dispositionEvidence,
		@Size(max = 120) String dispositionOwner,
		@Size(max = 1000) String rootCause,
		@Size(max = 1000) String correctiveAction,
		@Size(max = 120) String actionOwner,
		LocalDate actionDueDate,
		@Size(max = 1000) String actionCompletionEvidence,
		Boolean effective,
		@Size(max = 1000) String verificationConclusion,
		@Size(max = 1000) String reason) { }
