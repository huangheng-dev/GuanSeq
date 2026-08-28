package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EquipmentAlertEvaluationService {

	private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneId.of("Asia/Shanghai"));

	private final EquipmentAlertRuleRepository ruleRepository;
	private final EquipmentAlertRepository alertRepository;
	private final EquipmentAlertEventRepository eventRepository;
	private final EquipmentAssetRepository assetRepository;
	private final EquipmentTelemetryPointRepository pointRepository;

	EquipmentAlertEvaluationService(EquipmentAlertRuleRepository ruleRepository,
			EquipmentAlertRepository alertRepository, EquipmentAlertEventRepository eventRepository,
			EquipmentAssetRepository assetRepository, EquipmentTelemetryPointRepository pointRepository) {
		this.ruleRepository = ruleRepository;
		this.alertRepository = alertRepository;
		this.eventRepository = eventRepository;
		this.assetRepository = assetRepository;
		this.pointRepository = pointRepository;
	}

	@Transactional
	void evaluateSamples(EquipmentTelemetryConnectionEntity connection, List<EquipmentTelemetrySampleEntity> samples) {
		if (samples.isEmpty()) return;
		Map<UUID, EquipmentTelemetrySampleEntity> latestByPoint = new LinkedHashMap<>();
		for (EquipmentTelemetrySampleEntity sample : samples) latestByPoint.put(sample.getPointId(), sample);
		for (EquipmentAlertRuleEntity rule : ruleRepository.findByConnectionIdAndStatus(connection.getId(), "ACTIVE")) {
			if ("COMMUNICATION_FAILURE".equals(rule.getRuleType())) continue;
			EquipmentTelemetrySampleEntity sample = latestByPoint.get(rule.getPointId());
			if (sample == null) continue;
			boolean active = switch (rule.getRuleType()) {
				case "HIGH_LIMIT" -> sample.getNumericValue() != null
						&& sample.getNumericValue().compareTo(rule.getThresholdValue()) >= 0;
				case "LOW_LIMIT" -> sample.getNumericValue() != null
						&& sample.getNumericValue().compareTo(rule.getThresholdValue()) <= 0;
				default -> false;
			};
			if (active) activate(rule, connection, sample.getNumericValue(), sample.getQuality(), null,
					sample.getReceivedAt(), "SAMPLE-" + sample.getId());
			else clear(rule, sample.getReceivedAt(), "SAMPLE-RECOVERY-" + sample.getId());
		}
	}

	@Transactional
	void communicationFailed(EquipmentTelemetryConnectionEntity connection, String failureCode) {
		for (EquipmentAlertRuleEntity rule : ruleRepository.findByConnectionIdAndStatus(connection.getId(), "ACTIVE")) {
			if ("COMMUNICATION_FAILURE".equals(rule.getRuleType())) {
				activate(rule, connection, null, null, stableFailureCode(failureCode), Instant.now(),
						"COMMUNICATION-FAILED-" + UUID.randomUUID());
			}
		}
	}

	@Transactional
	void communicationSucceeded(EquipmentTelemetryConnectionEntity connection) {
		Instant now = Instant.now();
		for (EquipmentAlertRuleEntity rule : ruleRepository.findByConnectionIdAndStatus(connection.getId(), "ACTIVE")) {
			if ("COMMUNICATION_FAILURE".equals(rule.getRuleType())) {
				clear(rule, now, "COMMUNICATION-RECOVERED-" + UUID.randomUUID());
			}
		}
	}

	private void activate(EquipmentAlertRuleEntity rule, EquipmentTelemetryConnectionEntity connection,
			BigDecimal value, String quality, String failureCode, Instant occurredAt, String requestId) {
		EquipmentAlertEntity alert = alertRepository.findByRuleIdAndStatusNot(rule.getId(), "CLOSED").orElse(null);
		if (alert == null) {
			EquipmentAssetEntity asset = assetRepository.findById(connection.getAssetId())
					.orElseThrow(() -> new IllegalStateException("报警规则关联设备不存在"));
			EquipmentTelemetryPointEntity point = rule.getPointId() == null ? null
					: pointRepository.findById(rule.getPointId())
							.orElseThrow(() -> new IllegalStateException("报警规则关联点位不存在"));
			alert = new EquipmentAlertEntity(nextNumber(occurredAt), rule, connection, asset, point, value, quality,
					failureCode, occurredAt);
			alertRepository.saveAndFlush(alert);
			eventRepository.saveAndFlush(new EquipmentAlertEventEntity(alert, null, "OCCURRED", null, "OPEN",
					triggerReason(rule), requestId, triggerDetails(value, quality, failureCode)));
			return;
		}
		if (alert.isConditionActive()) {
			alert.observe(value, quality, failureCode, occurredAt);
			alertRepository.save(alert);
			return;
		}
		String from = alert.getStatus();
		alert.reopen(value, quality, failureCode, occurredAt);
		alertRepository.saveAndFlush(alert);
		eventRepository.saveAndFlush(new EquipmentAlertEventEntity(alert, null, "REOPENED", from, "OPEN",
				"报警条件恢复后再次出现", requestId, triggerDetails(value, quality, failureCode)));
	}

	private void clear(EquipmentAlertRuleEntity rule, Instant occurredAt, String requestId) {
		EquipmentAlertEntity alert = alertRepository.findByRuleIdAndStatusNot(rule.getId(), "CLOSED").orElse(null);
		if (alert == null || !alert.isConditionActive()) return;
		String status = alert.getStatus();
		alert.clearCondition(occurredAt);
		alertRepository.saveAndFlush(alert);
		eventRepository.saveAndFlush(new EquipmentAlertEventEntity(alert, null, "CONDITION_CLEARED", status, status,
				"采集证据表明报警条件已恢复，仍需人工完成处置", requestId, Map.of("conditionActive", false)));
	}

	private static Map<String, Object> triggerDetails(BigDecimal value, String quality, String failureCode) {
		Map<String, Object> details = new LinkedHashMap<>();
		if (value != null) details.put("observedValue", value);
		if (quality != null) details.put("observedQuality", quality);
		if (failureCode != null) details.put("failureCode", failureCode);
		details.put("conditionActive", true);
		return Map.copyOf(details);
	}

	private static String triggerReason(EquipmentAlertRuleEntity rule) {
		return switch (rule.getRuleType()) {
			case "HIGH_LIMIT" -> "采集值达到或超过上限阈值";
			case "LOW_LIMIT" -> "采集值达到或低于下限阈值";
			case "COMMUNICATION_FAILURE" -> "生产协议采集失败";
			default -> "设备报警条件触发";
		};
	}

	private static String stableFailureCode(String value) {
		if (value == null || value.isBlank()) return "TELEMETRY_FAILURE";
		String normalized = value.trim();
		return normalized.length() <= 60 ? normalized : normalized.substring(0, 60);
	}

	private static String nextNumber(Instant occurredAt) {
		return "ALM-" + NUMBER_TIME.format(occurredAt) + "-"
				+ UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	}
}
