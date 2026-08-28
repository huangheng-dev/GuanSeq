package com.guanseq.labeling.api;

import java.util.List;
import java.util.UUID;

public record LabelReferenceData(
		List<String> allowedObjectTypes,
		List<Template> templates,
		List<Candidate> candidates) {

	public record Template(String objectType, String code, String name, String version, String paperSize) { }

	public record Candidate(String objectType, UUID objectId, long version, String code, String name,
			String detail, String payload, boolean hasPreparedRequest) { }
}

