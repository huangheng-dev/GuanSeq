package com.guanseq.equipment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EquipmentTelemetryConnectionRecord(
		UUID id,
		String connectionCode,
		String name,
		UUID assetId,
		String assetCode,
		String assetName,
		String protocol,
		String endpointType,
		String host,
		int port,
		int unitId,
		MqttConfiguration mqtt,
		int connectTimeoutMs,
		int readTimeoutMs,
		int pollIntervalSeconds,
		String status,
		String communicationStatus,
		Instant lastTestedAt,
		Instant lastTestSucceededAt,
		Instant lastAttemptAt,
		Instant lastSuccessAt,
		String lastErrorCode,
		String lastErrorMessage,
		long version,
		boolean canManage,
		List<Point> points,
		List<CurrentValue> currentValues,
		List<Event> events,
		Instant createdAt,
		Instant updatedAt) {

	public record MqttConfiguration(String transport, String clientId, int qos, String credentialReference,
			boolean credentialConfigured, String messageIdPointer, String deviceTimePointer) { }

	public record Point(UUID id, String pointCode, String name, String registerType, int address,
			String mqttTopic, String mqttValuePointer,
			String valueType, BigDecimal scale, BigDecimal valueOffset, String engineeringUnit,
			BigDecimal validMin, BigDecimal validMax, int sortOrder) { }

	public record CurrentValue(UUID pointId, String pointCode, String rawValue, BigDecimal numericValue,
			Boolean booleanValue, String quality, Instant deviceTime, Instant receivedAt,
			long sequenceNumber, int messageVersion, String sourceProtocol) { }

	public record Event(UUID id, UUID actorUserId, String action, String fromStatus, String toStatus,
			String reason, String requestId, Map<String, Object> details, Verification verification,
			Instant occurredAt) { }

	public record Verification(int verificationVersion, String evidenceLevel, boolean technicalPassed,
			boolean fieldAccepted, String protocol, String endpointType, int pointCount, int returnedPointCount,
			int warningCount, List<VerificationCheck> checks, List<String> pendingFieldChecks,
			String errorCode, String errorMessage) { }

	public record VerificationCheck(String code, String status, String message) { }

	public record CreateRequest(
			@NotBlank @Size(max = 40) String connectionCode,
			@NotBlank @Size(max = 120) String name,
			@NotNull UUID assetId,
			@NotNull @Pattern(regexp = "MODBUS_TCP|MQTT_3_1_1") String protocol,
			@NotNull @Pattern(regexp = "SIMULATOR|PHYSICAL_DEVICE|EXTERNAL_BROKER") String endpointType,
			@NotBlank @Size(max = 253) @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String host,
			@Min(1) @Max(65535) int port,
			@Min(0) @Max(247) int unitId,
			@Valid MqttConfigurationRequest mqtt,
			@Min(100) @Max(10000) int connectTimeoutMs,
			@Min(100) @Max(10000) int readTimeoutMs,
			@Min(1) @Max(3600) int pollIntervalSeconds,
			@NotEmpty @Size(min = 1, max = 100) List<@Valid PointRequest> points,
			@NotBlank @Size(min = 4, max = 500) String reason) { }

	public record PointRequest(
			@NotBlank @Size(max = 60) String pointCode,
			@NotBlank @Size(max = 120) String name,
			@NotNull @Pattern(regexp = "COIL|HOLDING_REGISTER|MQTT_JSON") String registerType,
			@Min(0) @Max(65535) int address,
			@Size(max = 512) String mqttTopic,
			@Size(max = 253) String mqttValuePointer,
			@NotNull @Pattern(regexp = "BOOLEAN|UINT16|INT16|UINT32|INT32|DECIMAL") String valueType,
			@NotNull @DecimalMin("-1000000000") @DecimalMax("1000000000") BigDecimal scale,
			@NotNull @DecimalMin("-1000000000") @DecimalMax("1000000000") BigDecimal valueOffset,
			@Size(max = 24) String engineeringUnit,
			BigDecimal validMin,
			BigDecimal validMax,
			@Min(1) @Max(1000) int sortOrder) { }

	public record MqttConfigurationRequest(
			@NotNull @Pattern(regexp = "TCP|TLS") String transport,
			@NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]+") String clientId,
			@Min(0) @Max(1) int qos,
			@Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String credentialReference,
			@NotBlank @Size(max = 253) @Pattern(regexp = "/.*") String messageIdPointer,
			@Size(max = 253) @Pattern(regexp = "/.*") String deviceTimePointer) { }

	public record ActionRequest(
			@NotBlank @Size(min = 4, max = 500) String reason,
			@PositiveOrZero long expectedVersion) { }

	public record ActionResult(boolean success, String message, EquipmentTelemetryConnectionRecord connection) { }
}
