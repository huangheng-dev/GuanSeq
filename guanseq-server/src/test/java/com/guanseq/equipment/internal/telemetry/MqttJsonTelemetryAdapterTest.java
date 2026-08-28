package com.guanseq.equipment.internal.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MqttJsonTelemetryAdapterTest {

	@Test
	void subscribesToAStandardBrokerAndMapsJsonValuesWithMessageEvidence() throws Exception {
		try (MqttTestBroker broker = new MqttTestBroker(payload("msg-001", "68.5"))) {
			MqttJsonTelemetryAdapter adapter = new MqttJsonTelemetryAdapter(new ObjectMapper(),
					new MqttCredentialResolver());
			TelemetryProtocolAdapter.ConnectionSpec connection = connection(broker.port(), "unit-mqtt-client");
			UUID loadId = UUID.randomUUID();
			UUID doorId = UUID.randomUUID();
			Map<UUID, TelemetryProtocolAdapter.RawValue> values = adapter.readAll(connection, List.of(
					new TelemetryProtocolAdapter.PointSpec(loadId, "MQTT_JSON", "factory/cnc/telemetry",
							"/values/SPINDLE_LOAD", "DECIMAL"),
					new TelemetryProtocolAdapter.PointSpec(doorId, "MQTT_JSON", "factory/cnc/telemetry",
							"/values/DOOR_CLOSED", "BOOLEAN")));

			assertThat(values.get(loadId).numericValue()).isEqualByComparingTo(new BigDecimal("68.5"));
			assertThat(values.get(loadId).sourceMessageId()).isEqualTo("msg-001");
			assertThat(values.get(loadId).deviceTime()).hasToString("2026-08-26T07:00:00Z");
			assertThat(values.get(doorId).booleanValue()).isTrue();

			broker.publish(payload("msg-002", "72.25"));
			Map<UUID, TelemetryProtocolAdapter.RawValue> next = adapter.readAll(connection, List.of(
					new TelemetryProtocolAdapter.PointSpec(loadId, "MQTT_JSON", "factory/cnc/telemetry",
							"/values/SPINDLE_LOAD", "DECIMAL"),
					new TelemetryProtocolAdapter.PointSpec(doorId, "MQTT_JSON", "factory/cnc/telemetry",
							"/values/DOOR_CLOSED", "BOOLEAN")));
			assertThat(next.get(loadId).numericValue()).isEqualByComparingTo("72.25");
			assertThat(next.get(loadId).sourceMessageId()).isEqualTo("msg-002");
			adapter.close(connection);
		}
	}

	@Test
	void rejectsInvalidJsonWithoutInventingValues() throws Exception {
		try (MqttTestBroker broker = new MqttTestBroker("not-json")) {
			MqttJsonTelemetryAdapter adapter = new MqttJsonTelemetryAdapter(new ObjectMapper(),
					new MqttCredentialResolver());
			assertThatThrownBy(() -> adapter.readAll(connection(broker.port(), "invalid-json-client"), List.of(
					new TelemetryProtocolAdapter.PointSpec(UUID.randomUUID(), "MQTT_JSON",
							"factory/cnc/telemetry", "/values/SPINDLE_LOAD", "DECIMAL"))))
					.isInstanceOfSatisfying(TelemetryProtocolAdapter.TelemetryProtocolException.class,
							exception -> assertThat(exception.code()).isEqualTo("MQTT_JSON_INVALID"));
		}
	}

	private static TelemetryProtocolAdapter.ConnectionSpec connection(int port, String clientId) {
		return new TelemetryProtocolAdapter.ConnectionSpec("127.0.0.1", port, 1000, 1000, Map.of(
				"mqttTransport", "TCP", "mqttClientId", clientId, "mqttQos", "0",
				"mqttMessageIdPointer", "/messageId", "mqttDeviceTimePointer", "/deviceTime"));
	}

	private static String payload(String messageId, String load) {
		return """
				{"messageId":"%s","deviceTime":"2026-08-26T07:00:00Z",
				 "values":{"SPINDLE_LOAD":%s,"DOOR_CLOSED":true}}
				""".formatted(messageId, load);
	}
}
