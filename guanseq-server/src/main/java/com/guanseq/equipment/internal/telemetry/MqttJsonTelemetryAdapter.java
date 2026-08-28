package com.guanseq.equipment.internal.telemetry;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class MqttJsonTelemetryAdapter implements TelemetryProtocolAdapter {

	private static final int MAX_PAYLOAD_BYTES = 65_536;
	private final ObjectMapper objectMapper;
	private final MqttCredentialResolver credentialResolver;
	private final Map<SessionKey, ActiveSession> sessions = new ConcurrentHashMap<>();

	MqttJsonTelemetryAdapter(ObjectMapper objectMapper, MqttCredentialResolver credentialResolver) {
		this.objectMapper = objectMapper;
		this.credentialResolver = credentialResolver;
	}

	@Override
	public String protocol() { return "MQTT_3_1_1"; }

	@Override
	public Map<UUID, RawValue> readAll(ConnectionSpec connection, List<PointSpec> points) {
		if (points.isEmpty()) throw new TelemetryProtocolException("NO_POINTS", "连接没有可订阅点位");
		MqttConfiguration configuration = configuration(connection);
		Set<String> topics = new LinkedHashSet<>();
		for (PointSpec point : points) {
			if (!"MQTT_JSON".equals(point.sourceType())) {
				throw new TelemetryProtocolException("INVALID_CONFIGURATION", "MQTT 点位来源类型无效");
			}
			if (point.sourceAddress() == null || point.sourceAddress().isBlank()) {
				throw new TelemetryProtocolException("INVALID_CONFIGURATION", "MQTT 点位缺少 Topic");
			}
			topics.add(point.sourceAddress());
		}
		SessionKey key = new SessionKey(connection.host(), connection.port(), configuration.transport(),
				configuration.clientId(), configuration.credentialReference(), configuration.qos());
		ActiveSession session = sessions.compute(key, (ignored, existing) -> {
			if (existing != null && existing.connected()) return existing;
			if (existing != null) existing.close();
			return open(connection, configuration, topics);
		});
		try {
			return session.read(points, connection.readTimeoutMs(), configuration, objectMapper);
		} catch (TelemetryProtocolException exception) {
			if (!session.connected()) sessions.remove(key, session);
			throw exception;
		}
	}

	@Override
	public void close(ConnectionSpec connection) {
		MqttConfiguration configuration = configuration(connection);
		SessionKey key = new SessionKey(connection.host(), connection.port(), configuration.transport(),
				configuration.clientId(), configuration.credentialReference(), configuration.qos());
		ActiveSession session = sessions.remove(key);
		if (session != null) session.close();
	}

	@PreDestroy
	void closeAll() {
		sessions.values().forEach(ActiveSession::close);
		sessions.clear();
	}

	private ActiveSession open(ConnectionSpec connection, MqttConfiguration configuration, Set<String> topics) {
		Mqtt3ClientBuilder builder = Mqtt3Client.builder()
				.identifier(configuration.clientId())
				.serverHost(connection.host())
				.serverPort(connection.port());
		builder.transportConfig()
				.socketConnectTimeout(connection.connectTimeoutMs(), TimeUnit.MILLISECONDS)
				.mqttConnectTimeout(connection.connectTimeoutMs(), TimeUnit.MILLISECONDS)
				.applyTransportConfig();
		if ("TLS".equals(configuration.transport())) builder.sslWithDefaultConfig();
		MqttCredentialResolver.Credentials credentials = credentialResolver.resolve(configuration.credentialReference());
		if (credentials != null) {
			byte[] password = credentials.password().getBytes(StandardCharsets.UTF_8);
			try {
				builder.simpleAuth().username(credentials.username()).password(password).applySimpleAuth();
			} finally {
				Arrays.fill(password, (byte) 0);
			}
		}
		Mqtt3AsyncClient client = builder.buildAsync();
		ActiveSession session = new ActiveSession(client, topics);
		try {
			client.connectWith().cleanSession(false).keepAlive(30).send()
					.get(connection.connectTimeoutMs(), TimeUnit.MILLISECONDS);
			MqttQos qos = configuration.qos() == 1 ? MqttQos.AT_LEAST_ONCE : MqttQos.AT_MOST_ONCE;
			for (String topic : topics) {
				client.subscribeWith().topicFilter(topic).qos(qos)
						.callback(publish -> session.offer(topic, publish)).send()
						.get(connection.connectTimeoutMs(), TimeUnit.MILLISECONDS);
			}
			return session;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			session.close();
			throw new TelemetryProtocolException("MQTT_INTERRUPTED", "MQTT 连接被中断", exception);
		} catch (TimeoutException exception) {
			session.close();
			throw new TelemetryProtocolException("MQTT_CONNECT_TIMEOUT", "MQTT Broker 连接或订阅超时", exception);
		} catch (ExecutionException | CompletionException exception) {
			session.close();
			throw new TelemetryProtocolException("MQTT_CONNECTION_FAILED", "无法连接或订阅 MQTT Broker", exception);
		}
	}

	private static MqttConfiguration configuration(ConnectionSpec connection) {
		int qos;
		try {
			qos = Integer.parseInt(connection.requiredParameter("mqttQos"));
		} catch (NumberFormatException exception) {
			throw new TelemetryProtocolException("INVALID_CONFIGURATION", "MQTT QoS 必须是整数", exception);
		}
		return new MqttConfiguration(connection.requiredParameter("mqttTransport"),
				connection.requiredParameter("mqttClientId"), qos,
				nullable(connection.parameters().get("credentialReference")),
				connection.requiredParameter("mqttMessageIdPointer"),
				nullable(connection.parameters().get("mqttDeviceTimePointer")));
	}

	private static String nullable(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private record MqttConfiguration(String transport, String clientId, int qos, String credentialReference,
			String messageIdPointer, String deviceTimePointer) { }

	private record SessionKey(String host, int port, String transport, String clientId,
			String credentialReference, int qos) { }

	private static final class ActiveSession {
		private final Mqtt3AsyncClient client;
		private final Map<String, ArrayBlockingQueue<InboundMessage>> messages = new LinkedHashMap<>();

		private ActiveSession(Mqtt3AsyncClient client, Set<String> topics) {
			this.client = client;
			for (String topic : topics) messages.put(topic, new ArrayBlockingQueue<>(100));
		}

		private boolean connected() {
			MqttClientState state = client.getConfig().getState();
			return state.isConnected();
		}

		private void offer(String topic, Mqtt3Publish publish) {
			ArrayBlockingQueue<InboundMessage> queue = messages.get(topic);
			if (queue == null) return;
			InboundMessage message = new InboundMessage(publish.getPayloadAsBytes());
			if (!queue.offer(message)) {
				queue.poll();
				queue.offer(message);
			}
		}

		private synchronized Map<UUID, RawValue> read(List<PointSpec> points, int timeoutMs,
				MqttConfiguration configuration, ObjectMapper objectMapper) {
			long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
			Map<String, ParsedMessage> parsedByTopic = new LinkedHashMap<>();
			for (String topic : messages.keySet()) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) throw timeout(topic);
				InboundMessage inbound;
				try {
					inbound = messages.get(topic).poll(remaining, TimeUnit.NANOSECONDS);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new TelemetryProtocolException("MQTT_INTERRUPTED", "等待 MQTT 消息时被中断", exception);
				}
				if (inbound == null) throw timeout(topic);
				InboundMessage newer;
				while ((newer = messages.get(topic).poll()) != null) inbound = newer;
				parsedByTopic.put(topic, parse(inbound.payload(), configuration, objectMapper));
			}
			Map<UUID, RawValue> result = new LinkedHashMap<>();
			for (PointSpec point : points) {
				ParsedMessage message = parsedByTopic.get(point.sourceAddress());
				JsonNode value = message.root().at(point.valuePath());
				if (value.isMissingNode() || value.isNull()) {
					throw new TelemetryProtocolException("MQTT_VALUE_MISSING",
							"MQTT Payload 缺少点位字段：" + point.id());
				}
				if ("BOOLEAN".equals(point.valueType())) {
					if (!value.isBoolean()) throw valueType(point.id(), "布尔值");
					result.put(point.id(), new RawValue(value.asText(), null, value.booleanValue(),
							message.messageId(), message.deviceTime()));
				} else if ("DECIMAL".equals(point.valueType())) {
					if (!value.isNumber()) throw valueType(point.id(), "数值");
					BigDecimal numeric = value.decimalValue();
					result.put(point.id(), new RawValue(value.asText(), numeric, null,
							message.messageId(), message.deviceTime()));
				} else {
					throw new TelemetryProtocolException("UNSUPPORTED_VALUE_TYPE", "MQTT 点位值类型不受支持");
				}
			}
			return result;
		}

		private static ParsedMessage parse(byte[] payload, MqttConfiguration configuration,
				ObjectMapper objectMapper) {
			if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
				throw new TelemetryProtocolException("MQTT_PAYLOAD_SIZE", "MQTT Payload 为空或超过 64 KiB");
			}
			try {
				JsonNode root = objectMapper.readTree(payload);
				JsonNode idNode = root.at(configuration.messageIdPointer());
				if (!idNode.isValueNode() || idNode.isNull()) {
					throw new TelemetryProtocolException("MQTT_MESSAGE_ID_MISSING", "MQTT Payload 缺少消息编号");
				}
				String messageId = idNode.asText().trim();
				if (messageId.isEmpty() || messageId.length() > 160) {
					throw new TelemetryProtocolException("MQTT_MESSAGE_ID_INVALID", "MQTT 消息编号为空或超过 160 字符");
				}
				Instant deviceTime = null;
				if (configuration.deviceTimePointer() != null) {
					JsonNode timeNode = root.at(configuration.deviceTimePointer());
					if (!timeNode.isTextual()) {
						throw new TelemetryProtocolException("MQTT_DEVICE_TIME_INVALID", "MQTT 设备时间不是 ISO-8601 字符串");
					}
					deviceTime = Instant.parse(timeNode.textValue());
				}
				return new ParsedMessage(root, messageId, deviceTime);
			} catch (TelemetryProtocolException exception) {
				throw exception;
			} catch (JacksonException exception) {
				throw new TelemetryProtocolException("MQTT_JSON_INVALID", "MQTT Payload 不是有效 JSON", exception);
			} catch (DateTimeException exception) {
				throw new TelemetryProtocolException("MQTT_DEVICE_TIME_INVALID", "MQTT 设备时间格式无效", exception);
			}
		}

		private static TelemetryProtocolException timeout(String topic) {
			return new TelemetryProtocolException("MQTT_MESSAGE_TIMEOUT", "限定时间内没有收到 MQTT Topic 消息：" + topic);
		}

		private static TelemetryProtocolException valueType(UUID pointId, String expected) {
			return new TelemetryProtocolException("MQTT_VALUE_TYPE_INVALID",
					"MQTT 点位 " + pointId + " 不是预期的" + expected);
		}

		private void close() {
			try {
				if (connected()) client.disconnect().get(2, TimeUnit.SECONDS);
			} catch (Exception ignored) {
				// 会话关闭失败不覆盖原业务动作；客户端状态将在进程内丢弃。
			}
		}
	}

	private record InboundMessage(byte[] payload) { }
	private record ParsedMessage(JsonNode root, String messageId, Instant deviceTime) { }
}
