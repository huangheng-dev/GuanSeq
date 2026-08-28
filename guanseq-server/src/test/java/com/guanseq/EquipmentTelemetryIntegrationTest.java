package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "guanseq.telemetry.polling-enabled=false")
class EquipmentTelemetryIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	EquipmentTelemetryIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void createsTestsActivatesAndPollsTheSameProtocolPathUsedByPhysicalDevices() throws Exception {
		try (ModbusSimulator simulator = new ModbusSimulator()) {
			mockMvc.perform(get("/api/v1/equipment/telemetry-connections"))
					.andExpect(status().isUnauthorized());

			MvcResult created = mockMvc.perform(post("/api/v1/equipment/telemetry-connections")
						.with(httpBasic(USERNAME, PASSWORD))
						.header("X-Request-Id", "telemetry-create-001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("TEL-MODBUS-001", simulator.port())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.endpointType").value("SIMULATOR"))
					.andExpect(jsonPath("$.protocol").value("MODBUS_TCP"))
					.andExpect(jsonPath("$.status").value("DRAFT"))
					.andExpect(jsonPath("$.communicationStatus").value("UNKNOWN"))
					.andExpect(jsonPath("$.points.length()").value(3))
					.andExpect(jsonPath("$.currentValues.length()").value(0))
					.andReturn();
			String id = extractId(created);

			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/activate", id)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("尚未测试不能启用", 0)))
					.andExpect(status().isConflict());

			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/test", id)
						.with(httpBasic(USERNAME, PASSWORD))
						.header("X-Request-Id", "telemetry-test-001")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("验证仿真端点和全部点位", 0)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.message").value("仿真技术预检通过；该结果不构成现场验收"))
					.andExpect(jsonPath("$.connection.communicationStatus").value("ONLINE"))
					.andExpect(jsonPath("$.connection.lastTestSucceededAt").isNotEmpty())
					.andExpect(jsonPath("$.connection.events[0].verification.evidenceLevel")
							.value("SIMULATION_TECHNICAL"))
					.andExpect(jsonPath("$.connection.events[0].verification.technicalPassed").value(true))
					.andExpect(jsonPath("$.connection.events[0].verification.fieldAccepted").value(false))
					.andExpect(jsonPath("$.connection.events[0].verification.pointCount").value(3))
					.andExpect(jsonPath("$.connection.events[0].verification.returnedPointCount").value(3))
					.andExpect(jsonPath("$.connection.events[0].verification.warningCount").value(1));

			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/activate", id)
						.with(httpBasic(USERNAME, PASSWORD))
						.header("X-Request-Id", "telemetry-activate-001")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("启用只读周期采集", 0)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("ACTIVE"))
					.andExpect(jsonPath("$.version").value(1));

			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/poll", id)
						.with(httpBasic(USERNAME, PASSWORD))
						.header("X-Request-Id", "telemetry-poll-001")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("立即采集验证归一化结果", 1)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.connection.currentValues.length()").value(3))
					.andExpect(jsonPath("$.connection.currentValues[?(@.pointCode == 'RUN_STATE')].numericValue").value(2.0))
					.andExpect(jsonPath("$.connection.currentValues[?(@.pointCode == 'SPINDLE_LOAD')].numericValue").value(68.5))
					.andExpect(jsonPath("$.connection.currentValues[?(@.pointCode == 'SPINDLE_LOAD')].quality").value("UNCERTAIN"))
					.andExpect(jsonPath("$.connection.currentValues[?(@.pointCode == 'DOOR_CLOSED')].booleanValue").value(true));

			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/pause", id)
						.with(httpBasic(USERNAME, PASSWORD))
						.header("X-Request-Id", "telemetry-pause-001")
						.contentType(MediaType.APPLICATION_JSON).content(actionBody("暂停仿真采集保留历史证据", 1)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("PAUSED"))
					.andExpect(jsonPath("$.version").value(2));

			mockMvc.perform(get("/api/v1/equipment/assets/{id}", "a1000000-0000-4000-8000-000000000001")
						.with(httpBasic(USERNAME, PASSWORD)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.operatingStatus").value("RUNNING"));
		}
	}

	@Test
	void preservesFailedConnectionEvidenceWithoutCreatingGoodSamples() throws Exception {
		int unavailablePort;
		try (ServerSocket unused = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			unavailablePort = unused.getLocalPort();
		}
		MvcResult created = mockMvc.perform(post("/api/v1/equipment/telemetry-connections")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "telemetry-create-offline-001")
				.contentType(MediaType.APPLICATION_JSON).content(createBody("TEL-OFFLINE-001", unavailablePort)))
			.andExpect(status().isCreated()).andReturn();
		String id = extractId(created);

		mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/test", id)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "telemetry-test-offline-001")
					.contentType(MediaType.APPLICATION_JSON).content(actionBody("验证断连证据能够保留", 0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.connection.communicationStatus").value("OFFLINE"))
			.andExpect(jsonPath("$.connection.lastErrorCode").value("CONNECTION_REFUSED"))
			.andExpect(jsonPath("$.connection.events[0].verification.technicalPassed").value(false))
			.andExpect(jsonPath("$.connection.events[0].verification.fieldAccepted").value(false))
			.andExpect(jsonPath("$.connection.events[0].verification.errorCode").value("CONNECTION_REFUSED"))
			.andExpect(jsonPath("$.connection.currentValues.length()").value(0));
	}

	@Test
	@Transactional
	void triggersRecoversAndClosesThresholdAndCommunicationAlertsWithoutChangingAssetFacts() throws Exception {
		try (ModbusSimulator simulator = new ModbusSimulator()) {
			MvcResult created = mockMvc.perform(post("/api/v1/equipment/telemetry-connections")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "alert-telemetry-create-001")
					.contentType(MediaType.APPLICATION_JSON).content(createBody("TEL-ALERT-001", simulator.port())))
					.andExpect(status().isCreated()).andReturn();
			String connectionId = extractId(created);
			String spindlePointId = extractPointId(created, "SPINDLE_LOAD");
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/test", connectionId)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("验证报警采集端点", 0)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/activate", connectionId)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("启用报警采集连接", 0)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

			MvcResult rule = mockMvc.perform(post("/api/v1/equipment/alert-rules")
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "alert-rule-create-001")
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"ruleCode":"LOAD_HIGH_001","name":"主轴负载过高","connectionId":"%s",
								 "pointId":"%s","ruleType":"HIGH_LIMIT","thresholdValue":60,
								 "severity":"WARNING","defaultAssignee":"设备主管","reason":"建立主轴负载越限责任"}
								""".formatted(connectionId, spindlePointId)))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
					.andExpect(jsonPath("$.events[0].action").value("CREATED")).andReturn();
			String ruleId = extractId(rule);

			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/poll", connectionId)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("采集越限值触发报警", 1)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

			MvcResult alertPage = mockMvc.perform(get("/api/v1/equipment/alerts")
						.with(httpBasic(USERNAME, PASSWORD)).param("status", "OPEN"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.activeConditionCount").value(1))
					.andExpect(jsonPath("$.items[?(@.ruleId == '%s')].conditionActive".formatted(ruleId)).value(true))
					.andExpect(jsonPath("$.items[?(@.ruleId == '%s')].observedValue".formatted(ruleId)).value(68.5))
					.andReturn();
			String alertId = extractFirstItemId(alertPage);

			mockMvc.perform(post("/api/v1/equipment/alerts/{id}/actions", alertId)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "alert-ack-001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(alertActionBody("ACKNOWLEDGE", "确认主轴负载报警责任", 0, "设备主管", null)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
					.andExpect(jsonPath("$.version").value(1));
			jdbcTemplate.update("""
					update equipment.maintenance_work_orders
					set asset_id = cast(? as uuid), asset_code_snapshot = 'EQ-CNC-001',
					    asset_name_snapshot = '一号精密加工中心', asset_location_snapshot = '机加车间 A-01'
					where id = cast(? as uuid)
					""", "a1000000-0000-4000-8000-000000000001", "b1000000-0000-4000-8000-000000000003");
			mockMvc.perform(post("/api/v1/equipment/alerts/{id}/actions", alertId)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "alert-link-repair-001")
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"action":"LINK_REPAIR","reason":"关联同设备现有维修责任","expectedVersion":1,
								 "workOrderId":"b1000000-0000-4000-8000-000000000003"}
								"""))
					.andExpect(status().isOk()).andExpect(jsonPath("$.linkedWorkOrderNumber").value("WO-20260825-0001"))
					.andExpect(jsonPath("$.version").value(2));
			mockMvc.perform(post("/api/v1/equipment/alerts/{id}/actions", alertId)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "alert-start-001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(alertActionBody("START_PROCESSING", "开始检查主轴负载原因", 2, null, null)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
					.andExpect(jsonPath("$.version").value(3));
			mockMvc.perform(post("/api/v1/equipment/alerts/{id}/actions", alertId)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(alertActionBody("RESOLVE", "条件仍存在时不得解决", 3, null, "尚未恢复")))
					.andExpect(status().isConflict());

			simulator.setHoldingRegister(2, 500);
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/poll", connectionId)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("采集恢复值保留条件恢复证据", 1)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
			mockMvc.perform(get("/api/v1/equipment/alerts/{id}", alertId).with(httpBasic(USERNAME, PASSWORD)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.conditionActive").value(false))
					.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
					.andExpect(jsonPath("$.version").value(4))
					.andExpect(jsonPath("$.events[0].action").value("CONDITION_CLEARED"));
			mockMvc.perform(post("/api/v1/equipment/alerts/{id}/actions", alertId)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "alert-resolve-001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(alertActionBody("RESOLVE", "确认负载已恢复正常", 4, null, "清理加工参数后负载稳定")))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"))
					.andExpect(jsonPath("$.version").value(5));
			mockMvc.perform(post("/api/v1/equipment/alerts/{id}/actions", alertId)
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "alert-close-001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(alertActionBody("CLOSE", "关闭已解决报警责任", 5, null, null)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
					.andExpect(jsonPath("$.events.length()").value(7));

			mockMvc.perform(post("/api/v1/equipment/alert-rules")
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "alert-rule-comm-create-001")
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"ruleCode":"COMM_FAIL_001","name":"采集通讯失败","connectionId":"%s",
								 "pointId":null,"ruleType":"COMMUNICATION_FAILURE","thresholdValue":null,
								 "severity":"CRITICAL","defaultAssignee":"设备主管","reason":"建立通讯失败责任"}
								""".formatted(connectionId)))
					.andExpect(status().isCreated());
			simulator.setRejectConnections(true);
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/poll", connectionId)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("验证通讯失败报警", 1)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));
			mockMvc.perform(get("/api/v1/equipment/alerts").with(httpBasic(USERNAME, PASSWORD))
						.param("status", "OPEN").param("severity", "CRITICAL"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items[?(@.ruleCode == 'COMM_FAIL_001')].conditionActive").value(true));
			simulator.setRejectConnections(false);
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/poll", connectionId)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("验证通讯恢复报警证据", 1)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

			mockMvc.perform(get("/api/v1/equipment/assets/{id}", "a1000000-0000-4000-8000-000000000001")
						.with(httpBasic(USERNAME, PASSWORD)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.operatingStatus").value("RUNNING"));
		}
	}

	@Test
	@Transactional
	void queriesFiniteHistoryAndCleansExpiredSamplesWithPolicyConcurrencyAndIdempotency() throws Exception {
		try (ModbusSimulator simulator = new ModbusSimulator()) {
			MvcResult created = mockMvc.perform(post("/api/v1/equipment/telemetry-connections")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "telemetry-history-create-001")
					.contentType(MediaType.APPLICATION_JSON).content(createBody("TEL-HISTORY-001", simulator.port())))
					.andExpect(status().isCreated()).andReturn();
			String id = extractId(created);
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/test", id)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("验证历史样本测试端点", 0)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
			mockMvc.perform(post("/api/v1/equipment/telemetry-connections/{id}/poll", id)
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content(actionBody("生成历史样本验证数据", 0)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

			mockMvc.perform(get("/api/v1/equipment/telemetry-samples")
						.with(httpBasic(USERNAME, PASSWORD)).param("connectionId", id).param("size", "20"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(3))
					.andExpect(jsonPath("$.items[0].receivedAt").isNotEmpty())
					.andExpect(jsonPath("$.items[?(@.pointCode == 'SPINDLE_LOAD')].numericValue").value(68.5));
			mockMvc.perform(get("/api/v1/equipment/telemetry-samples")
						.with(httpBasic(USERNAME, PASSWORD)).param("connectionId", id)
						.param("pointCode", "DOOR_CLOSED").param("quality", "GOOD"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
					.andExpect(jsonPath("$.items[0].booleanValue").value(true));
			mockMvc.perform(get("/api/v1/equipment/telemetry-samples")
						.with(httpBasic(USERNAME, PASSWORD)).param("connectionId", id)
						.param("from", "2026-01-01T00:00:00Z").param("to", "2026-03-01T00:00:00Z"))
					.andExpect(status().isBadRequest());

			mockMvc.perform(get("/api/v1/equipment/telemetry-retention-policy")
						.with(httpBasic(USERNAME, PASSWORD)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.retentionDays").value(30))
					.andExpect(jsonPath("$.defaultPolicy").value(true))
					.andExpect(jsonPath("$.schedulerAvailable").value(false))
					.andExpect(jsonPath("$.automaticCleanupEnabled").value(false))
					.andExpect(jsonPath("$.cleanupIntervalHours").value(24))
					.andExpect(jsonPath("$.canManage").value(true));
			mockMvc.perform(put("/api/v1/equipment/telemetry-retention-policy")
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"retentionDays":30,"expectedVersion":0,"reason":"调度器关闭时不得启用自动清理",
								 "automaticCleanupEnabled":true,"cleanupIntervalHours":24}
								"""))
					.andExpect(status().isUnprocessableEntity());
			mockMvc.perform(put("/api/v1/equipment/telemetry-retention-policy")
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "telemetry-policy-update-001")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"retentionDays\":7,\"expectedVersion\":0,\"reason\":\"试点原始样本保留七天\"}"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.retentionDays").value(7))
					.andExpect(jsonPath("$.defaultPolicy").value(false))
					.andExpect(jsonPath("$.events[0].action").value("POLICY_UPDATED"));

			int aged = jdbcTemplate.update("""
					update equipment.telemetry_samples set received_at = now() - interval '8 days'
					where sequence_number = (
					  select min(sequence_number) from equipment.telemetry_samples where connection_id = cast(? as uuid)
					)
					""", id);
			org.assertj.core.api.Assertions.assertThat(aged).isEqualTo(1);

			String cleanupBody = "{\"expectedVersion\":0,\"reason\":\"清理超过七天的原始样本\"}";
			mockMvc.perform(post("/api/v1/equipment/telemetry-retention-policy/cleanup")
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "telemetry-cleanup-001")
						.contentType(MediaType.APPLICATION_JSON).content(cleanupBody))
					.andExpect(status().isOk()).andExpect(jsonPath("$.deletedSampleCount").value(1))
					.andExpect(jsonPath("$.replayed").value(false))
					.andExpect(jsonPath("$.policy.expiredSampleCount").value(0))
					.andExpect(jsonPath("$.policy.events[0].action").value("CLEANUP_COMPLETED"));
			mockMvc.perform(post("/api/v1/equipment/telemetry-retention-policy/cleanup")
						.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "telemetry-cleanup-001")
						.contentType(MediaType.APPLICATION_JSON).content(cleanupBody))
					.andExpect(status().isOk()).andExpect(jsonPath("$.deletedSampleCount").value(1))
					.andExpect(jsonPath("$.replayed").value(true));

			mockMvc.perform(put("/api/v1/equipment/telemetry-retention-policy")
						.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"retentionDays\":14,\"expectedVersion\":9,\"reason\":\"模拟过期版本修改\"}"))
					.andExpect(status().isConflict());
		}
	}

	@Test
	@Transactional
	void deniesConnectionManagementToReadOnlyWorkspaceRolesAndScopesAssets() throws Exception {
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'QUALITY_INSPECTOR' "
				+ "where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)",
				"20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		mockMvc.perform(get("/api/v1/equipment/telemetry-connections").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").isNumber())
				.andExpect(jsonPath("$.total").doesNotExist())
				.andExpect(jsonPath("$.canManage").value(false));
		mockMvc.perform(get("/api/v1/equipment/telemetry-retention-policy").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(false));
		mockMvc.perform(get("/api/v1/equipment/alerts").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(false));
		mockMvc.perform(put("/api/v1/equipment/telemetry-retention-policy").with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"retentionDays\":30,\"expectedVersion\":0,\"reason\":\"无权限策略修改\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/equipment/telemetry-retention-policy/automation/run-now")
					.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"expectedVersion\":0,\"reason\":\"无权限运行自动清理\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/equipment/telemetry-connections").with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(createBody("TEL-DENIED-001", 1502)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/equipment/alert-rules").with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content("""
							{"ruleCode":"DENIED_RULE","name":"无权限规则",
							 "connectionId":"a1000000-0000-4000-8000-000000000001","pointId":null,
							 "ruleType":"COMMUNICATION_FAILURE","thresholdValue":null,"severity":"WARNING",
							 "defaultAssignee":"设备主管","reason":"验证无权限规则管理"}
							"""))
				.andExpect(status().isForbidden());
	}

	private static String createBody(String connectionCode, int port) {
		return """
				{"connectionCode":"%s","name":"加工中心仿真只读连接",
				 "assetId":"a1000000-0000-4000-8000-000000000001","protocol":"MODBUS_TCP",
				 "endpointType":"SIMULATOR","host":"127.0.0.1","port":%d,"unitId":1,
				 "connectTimeoutMs":500,"readTimeoutMs":500,"pollIntervalSeconds":5,
				 "points":[
				  {"pointCode":"RUN_STATE","name":"运行状态","registerType":"HOLDING_REGISTER","address":0,
				   "valueType":"UINT16","scale":1,"valueOffset":0,"engineeringUnit":"状态","validMin":0,"validMax":4,"sortOrder":1},
				  {"pointCode":"SPINDLE_LOAD","name":"主轴负载","registerType":"HOLDING_REGISTER","address":2,
				   "valueType":"UINT16","scale":0.1,"valueOffset":0,"engineeringUnit":"%%","validMin":0,"validMax":60,"sortOrder":2},
				  {"pointCode":"DOOR_CLOSED","name":"防护门关闭","registerType":"COIL","address":0,
				   "valueType":"BOOLEAN","scale":1,"valueOffset":0,"engineeringUnit":null,"validMin":null,"validMax":null,"sortOrder":3}
				 ],"reason":"建立可替换真机端点的协议验证连接"}
				""".formatted(connectionCode, port);
	}

	private static String actionBody(String reason, long version) {
		return "{\"reason\":\"%s\",\"expectedVersion\":%d}".formatted(reason, version);
	}

	private static String alertActionBody(String action, String reason, long version, String assignee,
			String resolutionNotes) {
		return "{\"action\":\"%s\",\"reason\":\"%s\",\"expectedVersion\":%d,\"assignee\":%s,\"resolutionNotes\":%s}"
				.formatted(action, reason, version, jsonString(assignee), jsonString(resolutionNotes));
	}

	private static String jsonString(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private static String extractId(MvcResult result) throws Exception {
		String response = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(response);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + response);
		return matcher.group(1);
	}

	private static String extractPointId(MvcResult result, String pointCode) throws Exception {
		String response = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\",\\\"pointCode\\\":\\\""
				+ pointCode + "\\\"").matcher(response);
		if (!matcher.find()) throw new AssertionError("响应中缺少点位 " + pointCode + ": " + response);
		return matcher.group(1);
	}

	private static String extractFirstItemId(MvcResult result) throws Exception {
		String response = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"items\\\":\\[\\{\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(response);
		if (!matcher.find()) throw new AssertionError("分页响应中缺少首项 id: " + response);
		return matcher.group(1);
	}

	private static final class ModbusSimulator implements AutoCloseable {
		private final ServerSocket server;
		private final Map<Integer, Integer> holdingRegisters = new ConcurrentHashMap<>();
		private volatile boolean running = true;
		private volatile boolean rejectConnections;

		private ModbusSimulator() throws IOException {
			server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
			holdingRegisters.put(0, 2);
			holdingRegisters.put(2, 685);
			Thread.startVirtualThread(this::acceptLoop);
		}

		int port() { return server.getLocalPort(); }
		void setHoldingRegister(int address, int value) { holdingRegisters.put(address, value); }
		void setRejectConnections(boolean reject) { this.rejectConnections = reject; }

		private void acceptLoop() {
			while (running) {
				try {
					Socket socket = server.accept();
					if (rejectConnections) socket.close();
					else Thread.startVirtualThread(() -> handle(socket));
				} catch (IOException exception) {
					if (running) throw new IllegalStateException(exception);
				}
			}
		}

		private void handle(Socket socket) {
			try (socket;
					var input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
					var output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
				while (running) {
					int transactionId;
					try {
						transactionId = input.readUnsignedShort();
					} catch (EOFException exception) {
						return;
					}
					input.readUnsignedShort();
					input.readUnsignedShort();
					int unitId = input.readUnsignedByte();
					int function = input.readUnsignedByte();
					int address = input.readUnsignedShort();
					int quantity = input.readUnsignedShort();
					if (function == 1) {
						writeHeader(output, transactionId, unitId, function, 1);
						output.writeByte(address == 0 ? 1 : 0);
					} else if (function == 3) {
						writeHeader(output, transactionId, unitId, function, quantity * 2);
						for (int index = 0; index < quantity; index++) {
							output.writeShort(holdingRegisters.getOrDefault(address + index, 0));
						}
					} else {
						output.writeShort(transactionId);
						output.writeShort(0);
						output.writeShort(3);
						output.writeByte(unitId);
						output.writeByte(function | 0x80);
						output.writeByte(1);
					}
					output.flush();
				}
			} catch (IOException ignored) {
				// Client closes the socket after each test or polling batch.
			}
		}

		private static void writeHeader(DataOutputStream output, int transactionId, int unitId,
				int function, int byteCount) throws IOException {
			output.writeShort(transactionId);
			output.writeShort(0);
			output.writeShort(3 + byteCount);
			output.writeByte(unitId);
			output.writeByte(function);
			output.writeByte(byteCount);
		}

		@Override
		public void close() throws IOException {
			running = false;
			server.close();
		}
	}
}
