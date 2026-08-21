package com.guanseq.platform.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformStatusController {

	private final String version;

	public PlatformStatusController(@Value("${guanseq.build.version}") String version) {
		this.version = version;
	}

	@GetMapping("/status")
	public PlatformStatus status() {
		return new PlatformStatus("guanseq-server", "UP", version);
	}
}
