package com.guanseq.equipment.internal.telemetry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.guanseq.equipment.internal.EquipmentTelemetryApplicationService;

@Component
@ConditionalOnProperty(name = "guanseq.telemetry.polling-enabled", havingValue = "true")
class EquipmentTelemetryPollingJob {

	private final EquipmentTelemetryApplicationService service;

	EquipmentTelemetryPollingJob(EquipmentTelemetryApplicationService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${guanseq.telemetry.dispatch-delay-ms:1000}")
	void pollDueConnections() {
		service.pollDueConnections();
	}
}
