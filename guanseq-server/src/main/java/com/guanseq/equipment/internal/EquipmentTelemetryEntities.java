package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "equipment", name = "telemetry_connections")
class EquipmentTelemetryConnectionEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "owning_organization_id") private UUID owningOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "asset_id") private UUID assetId;
	@Column(name = "connection_code") private String connectionCode;
	private String name;
	private String protocol;
	@Column(name = "endpoint_type") private String endpointType;
	private String host;
	private int port;
	@Column(name = "unit_id") private int unitId;
	@Column(name = "connect_timeout_ms") private int connectTimeoutMs;
	@Column(name = "read_timeout_ms") private int readTimeoutMs;
	@Column(name = "poll_interval_seconds") private int pollIntervalSeconds;
	@Column(name = "mqtt_transport") private String mqttTransport;
	@Column(name = "mqtt_client_id") private String mqttClientId;
	@Column(name = "mqtt_qos") private Integer mqttQos;
	@Column(name = "credential_reference") private String credentialReference;
	@Column(name = "mqtt_message_id_pointer") private String mqttMessageIdPointer;
	@Column(name = "mqtt_device_time_pointer") private String mqttDeviceTimePointer;
	private String status;
	@Version private long version;
	@Column(name = "creation_request_id") private String creationRequestId;
	@Column(name = "created_by") private UUID createdBy;
	@Column(name = "created_at") private Instant createdAt;
	@Column(name = "updated_by") private UUID updatedBy;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentTelemetryConnectionEntity() { }

	EquipmentTelemetryConnectionEntity(UUID tenantId, UUID organizationId, UUID workspaceId, UUID assetId,
			String connectionCode, String name, String protocol, String endpointType, String host, int port,
			int unitId, int connectTimeoutMs, int readTimeoutMs, int pollIntervalSeconds,
			String mqttTransport, String mqttClientId, Integer mqttQos, String credentialReference,
			String mqttMessageIdPointer, String mqttDeviceTimePointer, String requestId, UUID actorId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = tenantId;
		this.owningOrganizationId = organizationId;
		this.workspaceId = workspaceId;
		this.assetId = assetId;
		this.connectionCode = connectionCode.trim().toUpperCase();
		this.name = name.trim();
		this.protocol = protocol;
		this.endpointType = endpointType;
		this.host = host.trim();
		this.port = port;
		this.unitId = unitId;
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
		this.pollIntervalSeconds = pollIntervalSeconds;
		this.mqttTransport = nullable(mqttTransport);
		this.mqttClientId = nullable(mqttClientId);
		this.mqttQos = mqttQos;
		this.credentialReference = nullable(credentialReference);
		this.mqttMessageIdPointer = nullable(mqttMessageIdPointer);
		this.mqttDeviceTimePointer = nullable(mqttDeviceTimePointer);
		this.status = "DRAFT";
		this.creationRequestId = requestId;
		this.createdBy = actorId;
		this.createdAt = Instant.now();
		this.updatedBy = actorId;
		this.updatedAt = this.createdAt;
	}

	void activate(UUID actorId) { changeStatus("ACTIVE", actorId); }
	void pause(UUID actorId) { changeStatus("PAUSED", actorId); }

	private void changeStatus(String nextStatus, UUID actorId) {
		this.status = nextStatus;
		this.updatedBy = actorId;
		this.updatedAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getTenantOrganizationId() { return tenantOrganizationId; }
	UUID getOwningOrganizationId() { return owningOrganizationId; }
	UUID getWorkspaceId() { return workspaceId; }
	UUID getAssetId() { return assetId; }
	String getConnectionCode() { return connectionCode; }
	String getName() { return name; }
	String getProtocol() { return protocol; }
	String getEndpointType() { return endpointType; }
	String getHost() { return host; }
	int getPort() { return port; }
	int getUnitId() { return unitId; }
	int getConnectTimeoutMs() { return connectTimeoutMs; }
	int getReadTimeoutMs() { return readTimeoutMs; }
	int getPollIntervalSeconds() { return pollIntervalSeconds; }
	String getMqttTransport() { return mqttTransport; }
	String getMqttClientId() { return mqttClientId; }
	Integer getMqttQos() { return mqttQos; }
	String getCredentialReference() { return credentialReference; }
	String getMqttMessageIdPointer() { return mqttMessageIdPointer; }
	String getMqttDeviceTimePointer() { return mqttDeviceTimePointer; }
	String getStatus() { return status; }
	long getVersion() { return version; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }

	private static String nullable(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

@Entity
@Table(schema = "equipment", name = "telemetry_points")
class EquipmentTelemetryPointEntity {
	@Id private UUID id;
	@Column(name = "connection_id") private UUID connectionId;
	@Column(name = "point_code") private String pointCode;
	private String name;
	@Column(name = "register_type") private String registerType;
	private int address;
	@Column(name = "value_type") private String valueType;
	private BigDecimal scale;
	@Column(name = "value_offset") private BigDecimal valueOffset;
	@Column(name = "engineering_unit") private String engineeringUnit;
	@Column(name = "valid_min") private BigDecimal validMin;
	@Column(name = "valid_max") private BigDecimal validMax;
	@Column(name = "sort_order") private int sortOrder;
	@Column(name = "mqtt_topic") private String mqttTopic;
	@Column(name = "mqtt_value_pointer") private String mqttValuePointer;
	@Column(name = "created_at") private Instant createdAt;

	protected EquipmentTelemetryPointEntity() { }

	EquipmentTelemetryPointEntity(UUID connectionId, String pointCode, String name, String registerType,
			int address, String valueType, BigDecimal scale, BigDecimal valueOffset, String engineeringUnit,
			BigDecimal validMin, BigDecimal validMax, int sortOrder, String mqttTopic, String mqttValuePointer) {
		this.id = UUID.randomUUID();
		this.connectionId = connectionId;
		this.pointCode = pointCode.trim().toUpperCase();
		this.name = name.trim();
		this.registerType = registerType;
		this.address = address;
		this.valueType = valueType;
		this.scale = scale;
		this.valueOffset = valueOffset;
		this.engineeringUnit = nullable(engineeringUnit);
		this.validMin = validMin;
		this.validMax = validMax;
		this.sortOrder = sortOrder;
		this.mqttTopic = nullable(mqttTopic);
		this.mqttValuePointer = nullable(mqttValuePointer);
		this.createdAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getConnectionId() { return connectionId; }
	String getPointCode() { return pointCode; }
	String getName() { return name; }
	String getRegisterType() { return registerType; }
	int getAddress() { return address; }
	String getValueType() { return valueType; }
	BigDecimal getScale() { return scale; }
	BigDecimal getValueOffset() { return valueOffset; }
	String getEngineeringUnit() { return engineeringUnit; }
	BigDecimal getValidMin() { return validMin; }
	BigDecimal getValidMax() { return validMax; }
	int getSortOrder() { return sortOrder; }
	String getMqttTopic() { return mqttTopic; }
	String getMqttValuePointer() { return mqttValuePointer; }

	private static String nullable(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

@Entity
@Table(schema = "equipment", name = "telemetry_connection_runtime")
class EquipmentTelemetryRuntimeEntity {
	@Id @Column(name = "connection_id") private UUID connectionId;
	@Column(name = "communication_status") private String communicationStatus;
	@Column(name = "last_tested_at") private Instant lastTestedAt;
	@Column(name = "last_test_succeeded_at") private Instant lastTestSucceededAt;
	@Column(name = "last_attempt_at") private Instant lastAttemptAt;
	@Column(name = "last_success_at") private Instant lastSuccessAt;
	@Column(name = "next_poll_at") private Instant nextPollAt;
	@Column(name = "last_error_code") private String lastErrorCode;
	@Column(name = "last_error_message") private String lastErrorMessage;
	@Column(name = "updated_at") private Instant updatedAt;

	protected EquipmentTelemetryRuntimeEntity() { }

	EquipmentTelemetryRuntimeEntity(UUID connectionId) {
		this.connectionId = connectionId;
		this.communicationStatus = "UNKNOWN";
		this.updatedAt = Instant.now();
	}

	void testSucceeded() {
		Instant now = Instant.now();
		this.communicationStatus = "ONLINE";
		this.lastTestedAt = now;
		this.lastTestSucceededAt = now;
		this.lastAttemptAt = now;
		this.lastSuccessAt = now;
		clearError();
		this.updatedAt = now;
	}

	void testFailed(String code, String message) {
		Instant now = Instant.now();
		this.communicationStatus = "OFFLINE";
		this.lastTestedAt = now;
		this.lastAttemptAt = now;
		this.lastErrorCode = code;
		this.lastErrorMessage = truncate(message);
		this.updatedAt = now;
	}

	void pollSucceeded(int intervalSeconds) {
		Instant now = Instant.now();
		this.communicationStatus = "ONLINE";
		this.lastAttemptAt = now;
		this.lastSuccessAt = now;
		this.nextPollAt = now.plusSeconds(intervalSeconds);
		clearError();
		this.updatedAt = now;
	}

	void pollFailed(int intervalSeconds, String code, String message) {
		Instant now = Instant.now();
		this.communicationStatus = "OFFLINE";
		this.lastAttemptAt = now;
		this.nextPollAt = now.plusSeconds(intervalSeconds);
		this.lastErrorCode = code;
		this.lastErrorMessage = truncate(message);
		this.updatedAt = now;
	}

	void scheduleNow() {
		this.nextPollAt = Instant.now();
		this.updatedAt = this.nextPollAt;
	}

	boolean latestTestSucceeded() {
		return lastTestedAt != null && lastTestSucceededAt != null && !lastTestSucceededAt.isBefore(lastTestedAt);
	}

	private void clearError() {
		this.lastErrorCode = null;
		this.lastErrorMessage = null;
	}

	private static String truncate(String value) {
		if (value == null || value.isBlank()) return "设备连接失败";
		String normalized = value.trim();
		return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
	}

	String getCommunicationStatus() { return communicationStatus; }
	Instant getLastTestedAt() { return lastTestedAt; }
	Instant getLastTestSucceededAt() { return lastTestSucceededAt; }
	Instant getLastAttemptAt() { return lastAttemptAt; }
	Instant getLastSuccessAt() { return lastSuccessAt; }
	Instant getNextPollAt() { return nextPollAt; }
	String getLastErrorCode() { return lastErrorCode; }
	String getLastErrorMessage() { return lastErrorMessage; }
}

@Entity
@Table(schema = "equipment", name = "telemetry_samples")
class EquipmentTelemetrySampleEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "sequence_number")
	private long sequenceNumber;
	private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "asset_id") private UUID assetId;
	@Column(name = "connection_id") private UUID connectionId;
	@Column(name = "point_id") private UUID pointId;
	@Column(name = "point_code") private String pointCode;
	@Column(name = "raw_value") private String rawValue;
	@Column(name = "numeric_value") private BigDecimal numericValue;
	@Column(name = "boolean_value") private Boolean booleanValue;
	private String quality;
	@Column(name = "device_time") private Instant deviceTime;
	@Column(name = "received_at") private Instant receivedAt;
	@Column(name = "message_version") private int messageVersion;
	@Column(name = "source_protocol") private String sourceProtocol;
	@Column(name = "source_message_id") private String sourceMessageId;

	protected EquipmentTelemetrySampleEntity() { }

	EquipmentTelemetrySampleEntity(EquipmentTelemetryConnectionEntity connection,
			EquipmentTelemetryPointEntity point, String rawValue, BigDecimal numericValue,
			Boolean booleanValue, String quality, Instant deviceTime, Instant receivedAt,
			String sourceMessageId) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = connection.getTenantOrganizationId();
		this.workspaceId = connection.getWorkspaceId();
		this.assetId = connection.getAssetId();
		this.connectionId = connection.getId();
		this.pointId = point.getId();
		this.pointCode = point.getPointCode();
		this.rawValue = rawValue;
		this.numericValue = numericValue;
		this.booleanValue = booleanValue;
		this.quality = quality;
		this.deviceTime = deviceTime;
		this.receivedAt = receivedAt;
		this.messageVersion = 1;
		this.sourceProtocol = connection.getProtocol();
		this.sourceMessageId = sourceMessageId;
	}

	long getSequenceNumber() { return sequenceNumber; }
	UUID getId() { return id; }
	UUID getPointId() { return pointId; }
	String getPointCode() { return pointCode; }
	String getRawValue() { return rawValue; }
	BigDecimal getNumericValue() { return numericValue; }
	Boolean getBooleanValue() { return booleanValue; }
	String getQuality() { return quality; }
	Instant getDeviceTime() { return deviceTime; }
	Instant getReceivedAt() { return receivedAt; }
	int getMessageVersion() { return messageVersion; }
	String getSourceProtocol() { return sourceProtocol; }
	String getSourceMessageId() { return sourceMessageId; }
}

@Entity
@Table(schema = "equipment", name = "telemetry_connection_events")
class EquipmentTelemetryConnectionEventEntity {
	@Id private UUID id;
	@Column(name = "tenant_organization_id") private UUID tenantOrganizationId;
	@Column(name = "workspace_id") private UUID workspaceId;
	@Column(name = "actor_user_id") private UUID actorUserId;
	@Column(name = "connection_id") private UUID connectionId;
	private String action;
	@Column(name = "from_status") private String fromStatus;
	@Column(name = "to_status") private String toStatus;
	private String reason;
	@Column(name = "request_id") private String requestId;
	@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> details;
	@Column(name = "occurred_at") private Instant occurredAt;

	protected EquipmentTelemetryConnectionEventEntity() { }

	EquipmentTelemetryConnectionEventEntity(EquipmentTelemetryConnectionEntity connection, UUID actorId,
			String action, String fromStatus, String toStatus, String reason, String requestId,
			Map<String, Object> details) {
		this.id = UUID.randomUUID();
		this.tenantOrganizationId = connection.getTenantOrganizationId();
		this.workspaceId = connection.getWorkspaceId();
		this.actorUserId = actorId;
		this.connectionId = connection.getId();
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason.trim();
		this.requestId = requestId;
		this.details = details;
		this.occurredAt = Instant.now();
	}

	UUID getId() { return id; }
	UUID getActorUserId() { return actorUserId; }
	String getAction() { return action; }
	String getFromStatus() { return fromStatus; }
	String getToStatus() { return toStatus; }
	String getReason() { return reason; }
	String getRequestId() { return requestId; }
	Map<String, Object> getDetails() { return details; }
	Instant getOccurredAt() { return occurredAt; }
}
