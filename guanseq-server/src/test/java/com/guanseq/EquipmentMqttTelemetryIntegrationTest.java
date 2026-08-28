package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.equipment.internal.telemetry.MqttTestBroker;
import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "guanseq.telemetry.polling-enabled=false")
class EquipmentMqttTelemetryIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	EquipmentMqttTelemetryIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void connectsToAnExternalBrokerAndDeduplicatesStableMessageIds() throws Exception {
		try (MqttTestBroker broker = new MqttTestBroker(payload("mqtt-msg-001", 68.5))) {
			MvcResult created = mockMvc.perform(post("/api/v1/equipment/telemetry-connections")
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "mqtt-create-001")
						.contentType(MediaType.APPLICATION_JSON).content(createBody("TEL-MQTT-001", broker.port())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.protocol").value("MQTT_3_1_1"))
					.andExpect(jsonPath("$.endpointType").value("SIMULATOR"))
					.andExpect(jsonPath("$.mqtt.transport").value("TCP"))
					.andExpect(jsonPath("$.mqtt.credentialConfigured").value(false))
					.andExpect(jsonPath("$.points[0].mqttTopic").value("factory/cnc/telemetry"))
					.andReturn();
			String id = extractId(created);

			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/test", id)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "mqtt-test-001")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("验证外部 Broker 和 JSON 映射", 0)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.connection.communicationStatus").value("ONLINE"))
					.andExpect(jsonPath("$.connection.events[0].verification.evidenceLevel")
							.value("SIMULATION_TECHNICAL"))
					.andExpect(jsonPath("$.connection.events[0].verification.technicalPassed").value(true))
					.andExpect(jsonPath("$.connection.events[0].verification.fieldAccepted").value(false))
					.andExpect(jsonPath("$.connection.events[0].verification.warningCount").value(0))
					.andExpect(jsonPath("$.connection.events[0].verification.checks[?(@.code == 'POINT_COVERAGE')].status")
							.value("PASSED"));

			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/activate", id)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "mqtt-activate-001")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("启用 MQTT 只读采集", 0)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

			broker.publish(payload("mqtt-msg-001", 68.5));
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/poll", id)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "mqtt-poll-001")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("采集第一条 MQTT 消息", 1)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.connection.currentValues.length()").value(3))
					.andExpect(jsonPath("$.connection.currentValues[?(@.pointCode == 'SPINDLE_LOAD')].numericValue")
							.value(68.5))
					.andExpect(jsonPath("$.connection.currentValues[?(@.pointCode == 'DOOR_CLOSED')].booleanValue")
							.value(true));
			assertThat(sampleCount(id)).isEqualTo(3);

			broker.publish(payload("mqtt-msg-001", 68.5));
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/poll", id)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "mqtt-poll-duplicate-001")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("重复消息不得新增样本", 1)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.message").value("消息已经处理，没有新增样本"));
			assertThat(sampleCount(id)).isEqualTo(3);

			broker.publish(payload("mqtt-msg-002", 72.25));
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/poll", id)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "mqtt-poll-002")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("采集下一条 MQTT 消息", 1)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.connection.currentValues[?(@.pointCode == 'SPINDLE_LOAD')].numericValue")
							.value(72.25));
			assertThat(sampleCount(id)).isEqualTo(6);

			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/pause", id)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "mqtt-pause-001")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("暂停 MQTT 连接并关闭会话", 1)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
		}
	}

	@Test
	void recordsInvalidPayloadAsConnectionFailureWithoutSamples() throws Exception {
		try (MqttTestBroker broker = new MqttTestBroker("not-json")) {
			MvcResult created = mockMvc.perform(post("/api/v1/equipment/telemetry-connections")
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "mqtt-invalid-create-001")
						.contentType(MediaType.APPLICATION_JSON).content(createBody("TEL-MQTT-INVALID", broker.port())))
					.andExpect(status().isCreated()).andReturn();
			String id = extractId(created);
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/test", id)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("验证无效 JSON 不生成样本", 0)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.connection.lastErrorCode").value("MQTT_JSON_INVALID"))
					.andExpect(jsonPath("$.connection.events[0].verification.technicalPassed").value(false))
					.andExpect(jsonPath("$.connection.events[0].verification.fieldAccepted").value(false))
					.andExpect(jsonPath("$.connection.events[0].verification.errorCode").value("MQTT_JSON_INVALID"))
					.andExpect(jsonPath("$.connection.currentValues.length()").value(0));
			assertThat(sampleCount(id)).isZero();
		}
	}

	private int sampleCount(String connectionId) {
		return jdbcTemplate.queryForObject("select count(*) from equipment.telemetry_samples where connection_id=cast(? as uuid)",
				Integer.class, connectionId);
	}

	private static String createBody(String code, int port) {
		return """
				{"connectionCode":"%s","name":"加工中心 MQTT 只读连接",
				 "assetId":"a1000000-0000-4000-8000-000000000001","protocol":"MQTT_3_1_1",
				 "endpointType":"SIMULATOR","host":"127.0.0.1","port":%d,"unitId":0,
				 "mqtt":{"transport":"TCP","clientId":"guanseq-test-%s","qos":0,
				   "credentialReference":null,"messageIdPointer":"/messageId","deviceTimePointer":"/deviceTime"},
				 "connectTimeoutMs":1000,"readTimeoutMs":1000,"pollIntervalSeconds":5,
				 "points":[
				  {"pointCode":"RUN_STATE","name":"运行状态","registerType":"MQTT_JSON","address":0,
				   "mqttTopic":"factory/cnc/telemetry","mqttValuePointer":"/values/RUN_STATE",
				   "valueType":"DECIMAL","scale":1,"valueOffset":0,"engineeringUnit":"状态","validMin":0,"validMax":4,"sortOrder":1},
				  {"pointCode":"SPINDLE_LOAD","name":"主轴负载","registerType":"MQTT_JSON","address":0,
				   "mqttTopic":"factory/cnc/telemetry","mqttValuePointer":"/values/SPINDLE_LOAD",
				   "valueType":"DECIMAL","scale":1,"valueOffset":0,"engineeringUnit":"%%","validMin":0,"validMax":100,"sortOrder":2},
				  {"pointCode":"DOOR_CLOSED","name":"防护门关闭","registerType":"MQTT_JSON","address":0,
				   "mqttTopic":"factory/cnc/telemetry","mqttValuePointer":"/values/DOOR_CLOSED",
				   "valueType":"BOOLEAN","scale":1,"valueOffset":0,"engineeringUnit":null,"validMin":null,"validMax":null,"sortOrder":3}
				 ],"reason":"建立用户可替换外部 Broker 的 MQTT 协议连接"}
				""".formatted(code, port, code.toLowerCase());
	}

	private static String payload(String messageId, double load) {
		return """
				{"messageId":"%s","deviceTime":"2026-08-26T07:00:00Z",
				 "values":{"RUN_STATE":2,"SPINDLE_LOAD":%s,"DOOR_CLOSED":true}}
				""".formatted(messageId, load);
	}

	private static String actionBody(String reason, long version) {
		return "{\"reason\":\"%s\",\"expectedVersion\":%d}".formatted(reason, version);
	}

	private static String extractId(MvcResult result) throws Exception {
		String response = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(response);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + response);
		return matcher.group(1);
	}
}
