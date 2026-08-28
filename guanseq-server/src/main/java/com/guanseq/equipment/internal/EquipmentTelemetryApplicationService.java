package com.guanseq.equipment.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.guanseq.equipment.api.EquipmentTelemetryConnectionPage;
import com.guanseq.equipment.api.EquipmentTelemetryConnectionRecord;
import com.guanseq.equipment.internal.telemetry.TelemetryProtocolAdapter;
import com.guanseq.equipment.internal.telemetry.TelemetryProtocolAdapter.TelemetryProtocolException;
import com.guanseq.identity.api.CurrentWorkspaceAccess;
import com.guanseq.identity.api.CurrentWorkspaceProvider;

@Service
public class EquipmentTelemetryApplicationService {

	private static final Set<String> MANAGER_ROLES = Set.of("MAINTENANCE_MANAGER", "ADMIN");
	private final CurrentWorkspaceProvider workspaceProvider;
	private final EquipmentAssetRepository assetRepository;
	private final EquipmentTelemetryConnectionRepository connectionRepository;
	private final EquipmentTelemetryPointRepository pointRepository;
	private final EquipmentTelemetryRuntimeRepository runtimeRepository;
	private final EquipmentTelemetrySampleRepository sampleRepository;
	private final EquipmentTelemetryConnectionEventRepository eventRepository;
	private final EquipmentAlertEvaluationService alertEvaluationService;
	private final Map<String, TelemetryProtocolAdapter> protocolAdapters;
	private final Map<UUID, ReentrantLock> pollingLocks = new ConcurrentHashMap<>();

	EquipmentTelemetryApplicationService(CurrentWorkspaceProvider workspaceProvider,
			EquipmentAssetRepository assetRepository,
			EquipmentTelemetryConnectionRepository connectionRepository,
			EquipmentTelemetryPointRepository pointRepository,
			EquipmentTelemetryRuntimeRepository runtimeRepository,
			EquipmentTelemetrySampleRepository sampleRepository,
			EquipmentTelemetryConnectionEventRepository eventRepository,
			EquipmentAlertEvaluationService alertEvaluationService,
			List<TelemetryProtocolAdapter> protocolAdapters) {
		this.workspaceProvider = workspaceProvider;
		this.assetRepository = assetRepository;
		this.connectionRepository = connectionRepository;
		this.pointRepository = pointRepository;
		this.runtimeRepository = runtimeRepository;
		this.sampleRepository = sampleRepository;
		this.eventRepository = eventRepository;
		this.alertEvaluationService = alertEvaluationService;
		this.protocolAdapters = protocolAdapters.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
				TelemetryProtocolAdapter::protocol, adapter -> adapter));
	}

	@Transactional(readOnly = true)
	public EquipmentTelemetryConnectionPage list(String username, int page, int size) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		var result = connectionRepository.findByTenantOrganizationIdAndWorkspaceId(
				access.tenantOrganizationId(), access.workspaceId(),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
						Sort.by(Sort.Direction.DESC, "updatedAt")));
		return new EquipmentTelemetryConnectionPage(result.getContent().stream()
				.map(connection -> toRecord(access, connection, false)).toList(), result.getTotalElements(),
				result.getNumber(), result.getSize(), result.getTotalPages(), canManage(access));
	}

	@Transactional(readOnly = true)
	public EquipmentTelemetryConnectionRecord get(String username, UUID id) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		return toRecord(access, requireConnection(access, id), true);
	}

	@Transactional
	public EquipmentTelemetryConnectionRecord create(String username,
			EquipmentTelemetryConnectionRecord.CreateRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		EquipmentAssetEntity asset = requireAsset(access, request.assetId());
		validateRequest(request);
		var mqtt = request.mqtt();
		String actionRequestId = requestId();
		EquipmentTelemetryConnectionEntity connection = new EquipmentTelemetryConnectionEntity(
				access.tenantOrganizationId(), access.operatingOrganizationId(), access.workspaceId(), asset.getId(),
				request.connectionCode(), request.name(), request.protocol(), request.endpointType(), request.host(),
				request.port(), request.unitId(), request.connectTimeoutMs(), request.readTimeoutMs(),
				request.pollIntervalSeconds(), mqtt == null ? null : mqtt.transport(),
				mqtt == null ? null : mqtt.clientId(), mqtt == null ? null : mqtt.qos(),
				mqtt == null ? null : mqtt.credentialReference(), mqtt == null ? null : mqtt.messageIdPointer(),
				mqtt == null ? null : mqtt.deviceTimePointer(), actionRequestId, access.userId());
		try {
			connectionRepository.saveAndFlush(connection);
			List<EquipmentTelemetryPointEntity> points = request.points().stream()
					.map(point -> new EquipmentTelemetryPointEntity(connection.getId(), point.pointCode(), point.name(),
							point.registerType(), point.address(), point.valueType(), point.scale(), point.valueOffset(),
							point.engineeringUnit(), point.validMin(), point.validMax(), point.sortOrder(),
							point.mqttTopic(), point.mqttValuePointer()))
					.toList();
			pointRepository.saveAllAndFlush(points);
			runtimeRepository.saveAndFlush(new EquipmentTelemetryRuntimeEntity(connection.getId()));
			audit(access, connection, "CREATED", null, "DRAFT", request.reason(),
					Map.of("endpointType", connection.getEndpointType(), "pointCount", points.size()), actionRequestId);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "连接编码、点位编码或点位顺序已存在，请核对后重试", exception);
		}
		return toRecord(access, connection, true);
	}

	@Transactional
	public EquipmentTelemetryConnectionRecord.ActionResult test(String username, UUID id,
			EquipmentTelemetryConnectionRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		EquipmentTelemetryConnectionEntity connection = requireConnection(access, id);
		requireVersion(connection, request.expectedVersion());
		EquipmentTelemetryRuntimeEntity runtime = requireRuntime(connection.getId());
		try {
			Map<UUID, TelemetryProtocolAdapter.RawValue> values = read(connection);
			runtime.testSucceeded();
			runtimeRepository.saveAndFlush(runtime);
			audit(access, connection, "TEST_SUCCEEDED", connection.getStatus(), connection.getStatus(),
					request.reason(), verificationDetails(connection, values, null), requestId());
			return new EquipmentTelemetryConnectionRecord.ActionResult(true, verificationMessage(connection),
					toRecord(access, connection, true));
		} catch (TelemetryProtocolException exception) {
			runtime.testFailed(exception.code(), exception.getMessage());
			runtimeRepository.saveAndFlush(runtime);
			audit(access, connection, "TEST_FAILED", connection.getStatus(), connection.getStatus(),
					request.reason(), verificationDetails(connection, Map.of(), exception), requestId());
			return new EquipmentTelemetryConnectionRecord.ActionResult(false, exception.getMessage(),
					toRecord(access, connection, true));
		}
	}

	@Transactional
	public EquipmentTelemetryConnectionRecord activate(String username, UUID id,
			EquipmentTelemetryConnectionRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		EquipmentTelemetryConnectionEntity connection = requireConnection(access, id);
		requireVersion(connection, request.expectedVersion());
		if ("ACTIVE".equals(connection.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "连接已经启用");
		}
		EquipmentTelemetryRuntimeEntity runtime = requireRuntime(connection.getId());
		if (!runtime.latestTestSucceeded()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "最近一次连接测试未成功，不能启用采集");
		}
		String from = connection.getStatus();
		connection.activate(access.userId());
		runtime.scheduleNow();
		connectionRepository.saveAndFlush(connection);
		runtimeRepository.saveAndFlush(runtime);
		audit(access, connection, "ACTIVATED", from, "ACTIVE", request.reason(), Map.of(), requestId());
		return toRecord(access, connection, true);
	}

	@Transactional
	public EquipmentTelemetryConnectionRecord pause(String username, UUID id,
			EquipmentTelemetryConnectionRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		EquipmentTelemetryConnectionEntity connection = requireConnection(access, id);
		requireVersion(connection, request.expectedVersion());
		if (!"ACTIVE".equals(connection.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "只有启用中的连接可以暂停");
		}
		connection.pause(access.userId());
		TelemetryProtocolAdapter adapter = protocolAdapters.get(connection.getProtocol());
		if (adapter != null) adapter.close(connectionSpec(connection));
		connectionRepository.saveAndFlush(connection);
		audit(access, connection, "PAUSED", "ACTIVE", "PAUSED", request.reason(), Map.of(), requestId());
		return toRecord(access, connection, true);
	}

	@Transactional
	public EquipmentTelemetryConnectionRecord.ActionResult poll(String username, UUID id,
			EquipmentTelemetryConnectionRecord.ActionRequest request) {
		CurrentWorkspaceAccess access = workspaceProvider.resolve(username);
		requireManager(access);
		EquipmentTelemetryConnectionEntity connection = requireConnection(access, id);
		requireVersion(connection, request.expectedVersion());
		EquipmentTelemetryRuntimeEntity runtime = requireRuntime(connection.getId());
		if (!runtime.latestTestSucceeded()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "连接尚未通过测试，不能采集");
		}
		PollOutcome outcome = collect(connection, runtime);
		audit(access, connection, "POLL_REQUESTED", connection.getStatus(), connection.getStatus(), request.reason(),
				Map.of("success", outcome.success(), "message", outcome.message()), requestId());
		return new EquipmentTelemetryConnectionRecord.ActionResult(outcome.success(), outcome.message(),
				toRecord(access, connection, true));
	}

	@Transactional
	public void pollDueConnections() {
		Instant now = Instant.now();
		for (EquipmentTelemetryConnectionEntity connection
				: connectionRepository.findTop50ByStatusOrderByUpdatedAtAsc("ACTIVE")) {
			EquipmentTelemetryRuntimeEntity runtime = requireRuntime(connection.getId());
			if (runtime.getNextPollAt() == null || !runtime.getNextPollAt().isAfter(now)) {
				collect(connection, runtime);
			}
		}
	}

	private PollOutcome collect(EquipmentTelemetryConnectionEntity connection,
			EquipmentTelemetryRuntimeEntity runtime) {
		ReentrantLock lock = pollingLocks.computeIfAbsent(connection.getId(), ignored -> new ReentrantLock());
		if (!lock.tryLock()) return new PollOutcome(false, "连接正在执行采集，请稍后重试");
		try {
			Map<UUID, TelemetryProtocolAdapter.RawValue> values = read(connection);
			List<EquipmentTelemetryPointEntity> points = pointRepository.findByConnectionIdOrderBySortOrder(connection.getId());
			Instant receivedAt = Instant.now();
			List<EquipmentTelemetrySampleEntity> samples = new ArrayList<>();
			for (EquipmentTelemetryPointEntity point : points) {
				TelemetryProtocolAdapter.RawValue raw = values.get(point.getId());
				if (raw.sourceMessageId() != null && sampleRepository
						.existsByConnectionIdAndPointIdAndSourceMessageId(
								connection.getId(), point.getId(), raw.sourceMessageId())) continue;
				BigDecimal normalized = raw.numericValue() == null ? null
						: raw.numericValue().multiply(point.getScale()).add(point.getValueOffset());
				String quality = quality(point, normalized);
				samples.add(new EquipmentTelemetrySampleEntity(connection, point, raw.rawValue(), normalized,
						raw.booleanValue(), quality, raw.deviceTime(), receivedAt, raw.sourceMessageId()));
			}
			if (!samples.isEmpty()) sampleRepository.saveAllAndFlush(samples);
			runtime.pollSucceeded(connection.getPollIntervalSeconds());
			runtimeRepository.saveAndFlush(runtime);
			alertEvaluationService.evaluateSamples(connection, samples);
			alertEvaluationService.communicationSucceeded(connection);
			return new PollOutcome(true, samples.isEmpty() ? "消息已经处理，没有新增样本"
					: "已采集 " + samples.size() + " 个点位");
		} catch (TelemetryProtocolException exception) {
			runtime.pollFailed(connection.getPollIntervalSeconds(), exception.code(), exception.getMessage());
			runtimeRepository.saveAndFlush(runtime);
			alertEvaluationService.communicationFailed(connection, exception.code());
			return new PollOutcome(false, exception.getMessage());
		} finally {
			lock.unlock();
		}
	}

	private Map<UUID, TelemetryProtocolAdapter.RawValue> read(EquipmentTelemetryConnectionEntity connection) {
		List<EquipmentTelemetryPointEntity> points = pointRepository.findByConnectionIdOrderBySortOrder(connection.getId());
		TelemetryProtocolAdapter adapter = protocolAdapters.get(connection.getProtocol());
		if (adapter == null) {
			throw new TelemetryProtocolException("UNSUPPORTED_PROTOCOL",
					"没有可用的采集协议适配器：" + connection.getProtocol());
		}
		Map<UUID, TelemetryProtocolAdapter.RawValue> values = adapter.readAll(connectionSpec(connection),
				points.stream().map(point -> new TelemetryProtocolAdapter.PointSpec(point.getId(),
						point.getRegisterType(), "MQTT_JSON".equals(point.getRegisterType())
								? point.getMqttTopic() : Integer.toString(point.getAddress()),
						point.getMqttValuePointer(), point.getValueType())).toList());
		List<String> missingPoints = points.stream()
				.filter(point -> values.get(point.getId()) == null)
				.map(EquipmentTelemetryPointEntity::getPointCode).toList();
		if (!missingPoints.isEmpty()) {
			throw new TelemetryProtocolException("POINT_COVERAGE_INCOMPLETE",
					"协议响应缺少点位：" + String.join("、", missingPoints));
		}
		for (EquipmentTelemetryPointEntity point : points) {
			TelemetryProtocolAdapter.RawValue value = values.get(point.getId());
			boolean validShape = "BOOLEAN".equals(point.getValueType())
					? value.booleanValue() != null : value.numericValue() != null;
			if (!validShape) {
				throw new TelemetryProtocolException("POINT_VALUE_TYPE_INVALID",
						"点位 " + point.getPointCode() + " 的协议值类型与配置不一致");
			}
		}
		return values;
	}

	private Map<String, Object> verificationDetails(EquipmentTelemetryConnectionEntity connection,
			Map<UUID, TelemetryProtocolAdapter.RawValue> values, TelemetryProtocolException failure) {
		List<EquipmentTelemetryPointEntity> points = pointRepository
				.findByConnectionIdOrderBySortOrder(connection.getId());
		boolean simulator = "SIMULATOR".equals(connection.getEndpointType());
		String evidenceLevel = simulator ? "SIMULATION_TECHNICAL" : "FIELD_CANDIDATE_PRECHECK";
		List<Map<String, String>> checks = new ArrayList<>();
		checks.add(verificationCheck("CONFIGURATION", "PASSED", "连接、协议和只读点位配置已通过服务端校验"));
		int warningCount = 0;
		if (failure == null) {
			checks.add(verificationCheck("PROTOCOL_READ", "PASSED", "生产协议适配器已完成一次只读读取"));
			checks.add(verificationCheck("POINT_COVERAGE", "PASSED",
					"已返回全部 " + points.size() + " 个配置点位"));
			checks.add(verificationCheck("VALUE_SHAPE", "PASSED", "所有点位值类型与映射配置一致"));
			int outOfRangeCount = 0;
			for (EquipmentTelemetryPointEntity point : points) {
				TelemetryProtocolAdapter.RawValue raw = values.get(point.getId());
				BigDecimal normalized = raw.numericValue() == null ? null
						: raw.numericValue().multiply(point.getScale()).add(point.getValueOffset());
				if ("UNCERTAIN".equals(quality(point, normalized))) outOfRangeCount++;
			}
			if (outOfRangeCount == 0) {
				checks.add(verificationCheck("VALUE_RANGE", "PASSED", "本次读取值均在配置范围内"));
			} else {
				warningCount++;
				checks.add(verificationCheck("VALUE_RANGE", "WARNING",
						outOfRangeCount + " 个点位超出配置范围，需现场复核量程与比例"));
			}
		} else {
			checks.add(verificationCheck("PROTOCOL_READ", "FAILED",
					failure.code() + "：" + failure.getMessage()));
		}
		Map<String, String> securityCheck = securityCheck(connection, simulator);
		checks.add(securityCheck);
		if ("WARNING".equals(securityCheck.get("status"))) warningCount++;
		checks.add(verificationCheck("EVIDENCE_BOUNDARY", "INFO",
				simulator ? "本次仅形成仿真技术证据，不构成现场验收"
						: "本次仅形成现场候选端点技术预检，仍需责任人完成现场验收"));

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("verificationVersion", 1);
		details.put("evidenceLevel", evidenceLevel);
		details.put("technicalPassed", failure == null);
		details.put("fieldAccepted", false);
		details.put("protocol", connection.getProtocol());
		details.put("endpointType", connection.getEndpointType());
		details.put("pointCount", points.size());
		details.put("returnedPointCount", values.size());
		details.put("warningCount", warningCount);
		details.put("checks", checks);
		details.put("pendingFieldChecks", List.of("厂商点位或 Topic Schema 确认", "现场网络与最小权限审批",
				"断连恢复与补传验证", "确认规模下的容量与保留验证", "现场责任人签字确认"));
		if (failure != null) {
			details.put("errorCode", failure.code());
			details.put("errorMessage", failure.getMessage());
		}
		return Map.copyOf(details);
	}

	private static Map<String, String> securityCheck(EquipmentTelemetryConnectionEntity connection,
			boolean simulator) {
		if (simulator) {
			return verificationCheck("TRANSPORT_SECURITY", "INFO", "仿真端点安全配置不替代现场网络与凭据验收");
		}
		if ("MQTT_3_1_1".equals(connection.getProtocol())) {
			boolean tls = "TLS".equals(connection.getMqttTransport());
			boolean credentialAlias = connection.getCredentialReference() != null;
			if (tls && credentialAlias) {
				return verificationCheck("TRANSPORT_SECURITY", "PASSED",
						"已配置 TLS 和服务端凭据别名；证书链与 ACL 仍需现场验收");
			}
			return verificationCheck("TRANSPORT_SECURITY", "WARNING",
					"生产 Broker 尚未同时配置 TLS 与凭据别名，必须完成安全评审");
		}
		return verificationCheck("TRANSPORT_SECURITY", "WARNING",
				"Modbus TCP 无原生加密，必须由现场隔离网段和访问控制保护");
	}

	private static Map<String, String> verificationCheck(String code, String status, String message) {
		return Map.of("code", code, "status", status, "message", message);
	}

	private static String verificationMessage(EquipmentTelemetryConnectionEntity connection) {
		return "SIMULATOR".equals(connection.getEndpointType())
				? "仿真技术预检通过；该结果不构成现场验收"
				: "现场候选端点技术预检通过；仍需完成现场验收";
	}

	private static TelemetryProtocolAdapter.ConnectionSpec connectionSpec(
			EquipmentTelemetryConnectionEntity connection) {
		Map<String, String> parameters = new java.util.LinkedHashMap<>();
		parameters.put("unitId", Integer.toString(connection.getUnitId()));
		if ("MQTT_3_1_1".equals(connection.getProtocol())) {
			parameters.put("mqttTransport", connection.getMqttTransport());
			parameters.put("mqttClientId", connection.getMqttClientId());
			parameters.put("mqttQos", Integer.toString(connection.getMqttQos()));
			if (connection.getCredentialReference() != null) {
				parameters.put("credentialReference", connection.getCredentialReference());
			}
			parameters.put("mqttMessageIdPointer", connection.getMqttMessageIdPointer());
			if (connection.getMqttDeviceTimePointer() != null) {
				parameters.put("mqttDeviceTimePointer", connection.getMqttDeviceTimePointer());
			}
		}
		return new TelemetryProtocolAdapter.ConnectionSpec(connection.getHost(), connection.getPort(),
				connection.getConnectTimeoutMs(), connection.getReadTimeoutMs(), Map.copyOf(parameters));
	}

	private EquipmentTelemetryConnectionRecord toRecord(CurrentWorkspaceAccess access,
			EquipmentTelemetryConnectionEntity connection, boolean includeDetails) {
		boolean manager = canManage(access);
		EquipmentAssetEntity asset = requireAsset(access, connection.getAssetId());
		EquipmentTelemetryRuntimeEntity runtime = requireRuntime(connection.getId());
		var points = includeDetails ? pointRepository.findByConnectionIdOrderBySortOrder(connection.getId()).stream()
				.map(point -> new EquipmentTelemetryConnectionRecord.Point(point.getId(), point.getPointCode(),
						point.getName(), point.getRegisterType(), point.getAddress(),
						manager ? point.getMqttTopic() : null, manager ? point.getMqttValuePointer() : null,
						point.getValueType(),
						point.getScale(), point.getValueOffset(), point.getEngineeringUnit(), point.getValidMin(),
						point.getValidMax(), point.getSortOrder())).toList() : List.<EquipmentTelemetryConnectionRecord.Point>of();
		var currentValues = includeDetails ? sampleRepository.findLatestByConnectionId(connection.getId()).stream()
				.map(sample -> new EquipmentTelemetryConnectionRecord.CurrentValue(sample.getPointId(),
						sample.getPointCode(), sample.getRawValue(), sample.getNumericValue(), sample.getBooleanValue(),
						sample.getQuality(), sample.getDeviceTime(), sample.getReceivedAt(), sample.getSequenceNumber(),
						sample.getMessageVersion(), sample.getSourceProtocol())).toList()
				: List.<EquipmentTelemetryConnectionRecord.CurrentValue>of();
		var events = includeDetails ? eventRepository.findByConnectionIdOrderByOccurredAtDesc(connection.getId()).stream()
				.map(event -> new EquipmentTelemetryConnectionRecord.Event(event.getId(), event.getActorUserId(),
						event.getAction(), event.getFromStatus(), event.getToStatus(), event.getReason(),
						event.getRequestId(), event.getDetails(), toVerification(event), event.getOccurredAt())).toList()
				: List.<EquipmentTelemetryConnectionRecord.Event>of();
		EquipmentTelemetryConnectionRecord.MqttConfiguration mqtt = "MQTT_3_1_1".equals(connection.getProtocol())
				? new EquipmentTelemetryConnectionRecord.MqttConfiguration(connection.getMqttTransport(),
						connection.getMqttClientId(), connection.getMqttQos(),
						manager ? connection.getCredentialReference() : null,
						connection.getCredentialReference() != null, connection.getMqttMessageIdPointer(),
						connection.getMqttDeviceTimePointer()) : null;
		return new EquipmentTelemetryConnectionRecord(connection.getId(), connection.getConnectionCode(),
				connection.getName(), asset.getId(), asset.getAssetCode(), asset.getAssetName(), connection.getProtocol(),
				connection.getEndpointType(), manager ? connection.getHost() : null, connection.getPort(),
				connection.getUnitId(), mqtt, connection.getConnectTimeoutMs(), connection.getReadTimeoutMs(),
				connection.getPollIntervalSeconds(), connection.getStatus(), runtime.getCommunicationStatus(),
				runtime.getLastTestedAt(), runtime.getLastTestSucceededAt(), runtime.getLastAttemptAt(),
				runtime.getLastSuccessAt(), runtime.getLastErrorCode(), runtime.getLastErrorMessage(),
				connection.getVersion(), manager, points, currentValues, events, connection.getCreatedAt(),
				connection.getUpdatedAt());
	}

	private static EquipmentTelemetryConnectionRecord.Verification toVerification(
			EquipmentTelemetryConnectionEventEntity event) {
		Map<String, Object> details = event.getDetails();
		if (details == null || !details.containsKey("verificationVersion")) return null;
		List<EquipmentTelemetryConnectionRecord.VerificationCheck> checks = detailList(details.get("checks")).stream()
				.filter(Map.class::isInstance).map(Map.class::cast)
				.map(check -> new EquipmentTelemetryConnectionRecord.VerificationCheck(
						detailString(check.get("code")), detailString(check.get("status")),
						detailString(check.get("message")))).toList();
		List<String> pendingFieldChecks = detailList(details.get("pendingFieldChecks")).stream()
				.map(EquipmentTelemetryApplicationService::detailString).toList();
		return new EquipmentTelemetryConnectionRecord.Verification(detailInt(details.get("verificationVersion")),
				detailString(details.get("evidenceLevel")), Boolean.TRUE.equals(details.get("technicalPassed")),
				Boolean.TRUE.equals(details.get("fieldAccepted")), detailString(details.get("protocol")),
				detailString(details.get("endpointType")), detailInt(details.get("pointCount")),
				detailInt(details.get("returnedPointCount")), detailInt(details.get("warningCount")), checks,
				pendingFieldChecks, nullableDetailString(details.get("errorCode")),
				nullableDetailString(details.get("errorMessage")));
	}

	private static List<?> detailList(Object value) {
		return value instanceof List<?> list ? list : List.of();
	}

	private static int detailInt(Object value) {
		return value instanceof Number number ? number.intValue() : 0;
	}

	private static String detailString(Object value) {
		return value == null ? "" : value.toString();
	}

	private static String nullableDetailString(Object value) {
		return value == null ? null : value.toString();
	}

	private EquipmentAssetEntity requireAsset(CurrentWorkspaceAccess access, UUID assetId) {
		return assetRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(assetId,
				access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "设备不存在或不在当前工作区范围"));
	}

	private EquipmentTelemetryConnectionEntity requireConnection(CurrentWorkspaceAccess access, UUID id) {
		return connectionRepository.findByIdAndTenantOrganizationIdAndWorkspaceId(id,
				access.tenantOrganizationId(), access.workspaceId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集连接不存在或不在当前工作区范围"));
	}

	private EquipmentTelemetryRuntimeEntity requireRuntime(UUID connectionId) {
		return runtimeRepository.findById(connectionId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "采集连接运行状态缺失"));
	}

	private void audit(CurrentWorkspaceAccess access, EquipmentTelemetryConnectionEntity connection,
			String action, String fromStatus, String toStatus, String reason, Map<String, Object> details,
			String actionRequestId) {
		eventRepository.saveAndFlush(new EquipmentTelemetryConnectionEventEntity(connection, access.userId(), action,
				fromStatus, toStatus, reason, actionRequestId, details));
	}

	private static void validateRequest(EquipmentTelemetryConnectionRecord.CreateRequest request) {
		boolean mqtt = "MQTT_3_1_1".equals(request.protocol());
		if (mqtt) {
			if (request.mqtt() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MQTT 连接缺少协议配置");
			if (!Set.of("SIMULATOR", "EXTERNAL_BROKER").contains(request.endpointType())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MQTT 连接来源类型无效");
			}
			if (request.unitId() != 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MQTT 连接 unitId 必须为 0");
		} else {
			if (request.mqtt() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modbus 连接不能包含 MQTT 配置");
			if (!Set.of("SIMULATOR", "PHYSICAL_DEVICE").contains(request.endpointType())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modbus 连接来源类型无效");
			}
		}
		validatePoints(request.points(), mqtt);
	}

	private static void validatePoints(List<EquipmentTelemetryConnectionRecord.PointRequest> points, boolean mqtt) {
		Set<String> codes = new HashSet<>();
		Set<Integer> orders = new HashSet<>();
		for (var point : points) {
			String code = point.pointCode().trim().toUpperCase();
			if (!codes.add(code)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "点位编码不能重复");
			if (!orders.add(point.sortOrder())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "点位顺序不能重复");
			if (mqtt) {
				if (!"MQTT_JSON".equals(point.registerType()) || point.address() != 0
						|| !Set.of("BOOLEAN", "DECIMAL").contains(point.valueType())) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MQTT 点位必须使用 MQTT_JSON 和 BOOLEAN/DECIMAL");
				}
				String topic = point.mqttTopic() == null ? "" : point.mqttTopic().trim();
				if (topic.isEmpty() || topic.contains("+") || topic.contains("#")) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MQTT 点位必须配置不含通配符的精确 Topic");
				}
				if (point.mqttValuePointer() == null || !point.mqttValuePointer().startsWith("/")) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MQTT 点位必须配置 JSON Pointer");
				}
			} else {
				boolean coil = "COIL".equals(point.registerType());
				if ("MQTT_JSON".equals(point.registerType()) || coil != "BOOLEAN".equals(point.valueType())) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "线圈只能使用 BOOLEAN，寄存器不能使用 BOOLEAN");
				}
				if (point.mqttTopic() != null || point.mqttValuePointer() != null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modbus 点位不能包含 MQTT 映射");
				}
			}
			if (point.validMin() != null && point.validMax() != null
					&& point.validMin().compareTo(point.validMax()) > 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "点位合法最小值不能大于最大值");
			}
		}
	}

	private static String quality(EquipmentTelemetryPointEntity point, BigDecimal value) {
		if (value == null) return "GOOD";
		if (point.getValidMin() != null && value.compareTo(point.getValidMin()) < 0) return "UNCERTAIN";
		if (point.getValidMax() != null && value.compareTo(point.getValidMax()) > 0) return "UNCERTAIN";
		return "GOOD";
	}

	private static void requireManager(CurrentWorkspaceAccess access) {
		if (!canManage(access)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权管理设备采集连接");
		}
	}

	private static boolean canManage(CurrentWorkspaceAccess access) {
		return MANAGER_ROLES.contains(access.roleCode());
	}

	private static void requireVersion(EquipmentTelemetryConnectionEntity connection, long expectedVersion) {
		if (connection.getVersion() != expectedVersion) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "采集连接已被其他用户修改，请刷新后重试");
		}
	}

	private static String requestId() {
		String requestId = MDC.get("requestId");
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}

	private record PollOutcome(boolean success, String message) { }
}
