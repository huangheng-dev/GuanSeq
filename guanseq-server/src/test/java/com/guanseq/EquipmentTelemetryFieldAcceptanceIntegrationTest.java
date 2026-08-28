package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
class EquipmentTelemetryFieldAcceptanceIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String USER_ID = "20000000-0000-4000-8000-000000000001";
	private static final String WORKSPACE_ID = "10000000-0000-4000-8000-000000000101";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	EquipmentTelemetryFieldAcceptanceIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void rejectsSimulatorAsFieldEvidenceButKeepsContextReadable() throws Exception {
		String connectionId = createConnection("TFA-SIM-001", "SIMULATOR");
		mockMvc.perform(get("/api/v1/equipment/telemetry-field-acceptances/{id}", connectionId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/equipment/telemetry-field-acceptances/{id}", connectionId)
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fieldEligible").value(false))
				.andExpect(jsonPath("$.latestTechnicalPrecheckPassed").value(false))
				.andExpect(jsonPath("$.fieldAccepted").value(false))
				.andExpect(jsonPath("$.acceptance").value(org.hamcrest.Matchers.nullValue()));
		save(connectionId, "tfa-sim-save-001", draftBody(null, false), 422);
	}

	@Test
	void closesDraftPrecheckSubmissionApprovalConcurrencyAndIdempotencyLoop() throws Exception {
		String connectionId = createConnection("TFA-PHY-001", "PHYSICAL_DEVICE");
		save(connectionId, "tfa-create-001", draftBody(null, false), 200)
				.andExpect(jsonPath("$.acceptance.status").value("DRAFT"))
				.andExpect(jsonPath("$.acceptance.version").value(0))
				.andExpect(jsonPath("$.acceptance.events[0].action").value("CREATED"));
		act(connectionId, "tfa-submit-blocked-001", actionBody("SUBMIT", 0, "尚无真实预检不得提交"), 422);

		insertSuccessfulFieldPrecheck(connectionId, "tfa-field-precheck-001");
		mockMvc.perform(get("/api/v1/equipment/telemetry-field-acceptances/{id}", connectionId)
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.latestTechnicalPrecheckPassed").value(true));

		save(connectionId, "tfa-update-complete-001", draftBody(0L, true), 200)
				.andExpect(jsonPath("$.acceptance.version").value(1))
				.andExpect(jsonPath("$.acceptance.events[0].details.completedCheckCount").value(6));
		save(connectionId, "tfa-update-stale-001", draftBody(0L, true), 409);
		act(connectionId, "tfa-submit-001", actionBody("SUBMIT", 1, "设备组完成六项现场证据核对"), 200)
				.andExpect(jsonPath("$.acceptance.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.acceptance.version").value(2))
				.andExpect(jsonPath("$.acceptance.availableActions[0]").value("APPROVE"));
		act(connectionId, "tfa-approve-001", actionBody("APPROVE", 2, "生产经理复核真实现场证据通过"), 200)
				.andExpect(jsonPath("$.fieldAccepted").value(true))
				.andExpect(jsonPath("$.acceptance.status").value("APPROVED"))
				.andExpect(jsonPath("$.acceptance.version").value(3))
				.andExpect(jsonPath("$.acceptance.availableActions").isEmpty());
		act(connectionId, "tfa-approve-001", actionBody("APPROVE", 2, "生产经理复核真实现场证据通过"), 200)
				.andExpect(jsonPath("$.acceptance.version").value(3));
		Integer eventCount = jdbcTemplate.queryForObject(
				"select count(*) from equipment.telemetry_field_acceptance_events event join equipment.telemetry_field_acceptances acceptance on acceptance.id = event.acceptance_id where acceptance.connection_id = cast(? as uuid)",
				Integer.class, connectionId);
		org.assertj.core.api.Assertions.assertThat(eventCount).isEqualTo(4);
	}

	@Test
	void blocksApprovalWhenANewerTechnicalPrecheckFailed() throws Exception {
		String connectionId = createConnection("TFA-FAIL-001", "PHYSICAL_DEVICE");
		insertSuccessfulFieldPrecheck(connectionId, "tfa-success-before-submit-001");
		save(connectionId, "tfa-fail-create-001", draftBody(null, true), 200);
		act(connectionId, "tfa-fail-submit-001", actionBody("SUBMIT", 0, "现场责任人提交完整验收证据"), 200);
		insertFailedFieldPrecheck(connectionId, "tfa-latest-failed-001");
		act(connectionId, "tfa-fail-approve-001", actionBody("APPROVE", 1, "失败预检之后不得批准验收"), 422);
		mockMvc.perform(get("/api/v1/equipment/telemetry-field-acceptances/{id}", connectionId)
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.latestTechnicalPrecheckPassed").value(false))
				.andExpect(jsonPath("$.fieldAccepted").value(false))
				.andExpect(jsonPath("$.acceptance.status").value("SUBMITTED"));
	}

	@Test
	@Transactional
	void keepsReadsAvailableButEnforcesMaintenanceAndApprovalRoles() throws Exception {
		String connectionId = createConnection("TFA-ROLE-001", "PHYSICAL_DEVICE");
		insertSuccessfulFieldPrecheck(connectionId, "tfa-role-precheck-001");
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'QUALITY_INSPECTOR' where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)", USER_ID, WORKSPACE_ID);
		mockMvc.perform(get("/api/v1/equipment/telemetry-field-acceptances/{id}", connectionId)
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.canMaintain").value(false))
				.andExpect(jsonPath("$.canApprove").value(false));
		save(connectionId, "tfa-role-denied-001", draftBody(null, true), 403);
	}

	private String createConnection(String code, String endpointType) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/telemetry-connections")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", code.toLowerCase() + "-create")
					.contentType(MediaType.APPLICATION_JSON).content(connectionBody(code, endpointType)))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
	}

	private org.springframework.test.web.servlet.ResultActions save(String connectionId, String requestId,
			String body, int statusCode) throws Exception {
		return mockMvc.perform(put("/api/v1/equipment/telemetry-field-acceptances/{id}", connectionId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(statusCode));
	}

	private org.springframework.test.web.servlet.ResultActions act(String connectionId, String requestId,
			String body, int statusCode) throws Exception {
		return mockMvc.perform(post("/api/v1/equipment/telemetry-field-acceptances/{id}/actions", connectionId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(statusCode));
	}

	private void insertSuccessfulFieldPrecheck(String connectionId, String requestId) {
		insertFieldPrecheck(connectionId, requestId, true);
	}

	private void insertFailedFieldPrecheck(String connectionId, String requestId) {
		insertFieldPrecheck(connectionId, requestId, false);
	}

	private void insertFieldPrecheck(String connectionId, String requestId, boolean passed) {
		String details = "{\"verificationVersion\":1,\"evidenceLevel\":\"FIELD_CANDIDATE_PRECHECK\",\"technicalPassed\":"
				+ passed + ",\"fieldAccepted\":false,\"checks\":[],\"pendingFieldChecks\":[]}";
		jdbcTemplate.update("""
				insert into equipment.telemetry_connection_events
				(id, tenant_organization_id, workspace_id, actor_user_id, connection_id, action,
				 from_status, to_status, reason, request_id, details, occurred_at)
				select cast(? as uuid), connection.tenant_organization_id, connection.workspace_id, cast(? as uuid),
				 connection.id, ?, connection.status, connection.status, ?, ?, cast(? as jsonb), current_timestamp
				from equipment.telemetry_connections connection where connection.id = cast(? as uuid)
				""", UUID.randomUUID().toString(), USER_ID, passed ? "TEST_SUCCEEDED" : "TEST_FAILED",
				passed ? "集成测试构造已成功的真实现场预检前置事实" : "集成测试构造更新的失败现场预检事实",
				requestId, details, connectionId);
	}

	private static String connectionBody(String code, String endpointType) {
		return """
				{"connectionCode":"%s","name":"现场验收测试连接",
				 "assetId":"a1000000-0000-4000-8000-000000000001","protocol":"MODBUS_TCP",
				 "endpointType":"%s","host":"192.0.2.10","port":502,"unitId":1,
				 "connectTimeoutMs":500,"readTimeoutMs":500,"pollIntervalSeconds":30,
				 "points":[{"pointCode":"RUN_STATE","name":"运行状态","registerType":"HOLDING_REGISTER",
				 "address":0,"valueType":"UINT16","scale":1,"valueOffset":0,"engineeringUnit":"状态",
				 "validMin":0,"validMax":4,"sortOrder":1}],"reason":"建立现场验收状态机测试连接"}
				""".formatted(code, endpointType);
	}

	private static String draftBody(Long version, boolean complete) {
		return """
				{"networkApproved":%s,"securityValidated":%s,"readOnlyConfirmed":%s,
				 "disconnectRecoveryVerified":%s,"capacityVerified":%s,"pointMappingApproved":%s,
				 "responsibleOwner":%s,"testWindowStart":%s,"testWindowEnd":%s,
				 "evidenceReference":%s,"notes":"仅测试验收状态机，不代表任何真实设备已经接入",
				 "expectedVersion":%s,"reason":"%s"}
				""".formatted(complete, complete, complete, complete, complete, complete,
				complete ? "\"现场设备负责人\"" : "null",
				complete ? "\"2026-09-10T01:00:00Z\"" : "null",
				complete ? "\"2026-09-10T03:00:00Z\"" : "null",
				complete ? "\"SITE-TEST-REPORT-001\"" : "null",
				version == null ? "null" : version.toString(), complete ? "补齐六项真实现场验收证据" : "建立待现场核验的验收草稿");
	}

	private static String actionBody(String action, long version, String reason) {
		return "{\"action\":\"%s\",\"reason\":\"%s\",\"expectedVersion\":%d}"
				.formatted(action, reason, version);
	}

	private static String extractId(MvcResult result) throws Exception {
		String response = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(response);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + response);
		return matcher.group(1);
	}
}
