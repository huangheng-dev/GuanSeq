package com.guanseq.equipment.internal.telemetry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.guanseq.equipment.internal.EquipmentTelemetryRetentionAutomationService;

@Component
@ConditionalOnProperty(name = "guanseq.telemetry.retention-scheduler-enabled", havingValue = "true")
class EquipmentTelemetryRetentionJob {

	private final EquipmentTelemetryRetentionAutomationService service;

	EquipmentTelemetryRetentionJob(EquipmentTelemetryRetentionAutomationService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${guanseq.telemetry.retention-dispatch-delay-ms:60000}")
	void cleanupDueWorkspaces() {
		service.dispatchDuePolicies();
	}
}
