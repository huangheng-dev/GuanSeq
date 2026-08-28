package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EquipmentOeeIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String ASSET_ID = "a1000000-0000-4000-8000-000000000001";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	EquipmentOeeIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void closesManualOeeDowntimeAndApprovalEvidenceLoop() throws Exception {
		mockMvc.perform(get("/api/v1/equipment/oee-records")).andExpect(status().isUnauthorized());
		MvcResult created = create("oee-loop-create-001", "2026-09-01T00:00:00Z", "2026-09-01T08:00:00Z",
				480, 60, 400, 390).andExpect(status().isCreated())
				.andExpect(jsonPath("$.sourceType").value("MANUAL_VERIFIED"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.events[0].requestId").value("oee-loop-create-001")).andReturn();
		String id = extractId(created);

		act(id, "oee-loop-stop-001", addDowntime(0, "2026-09-01T02:00:00Z", "2026-09-01T02:30:00Z",
				"EQUIPMENT_FAILURE", "设备组", "主轴异常停机排查"), 200)
				.andExpect(jsonPath("$.downtimeMinutes").value(30.0)).andExpect(jsonPath("$.version").value(1));
		act(id, "oee-loop-stop-002", addDowntime(1, "2026-09-01T05:00:00Z", "2026-09-01T05:10:00Z",
				"MATERIAL_WAIT", "物料组", "等待关键物料配送"), 200)
				.andExpect(jsonPath("$.downtimeMinutes").value(40.0))
				.andExpect(jsonPath("$.availabilityRate").value(91.6667))
				.andExpect(jsonPath("$.performanceRate").value(90.9091))
				.andExpect(jsonPath("$.qualityRate").value(97.5))
				.andExpect(jsonPath("$.oeeRate").value(81.25));

		act(id, "oee-loop-submit-001", simpleAction("SUBMIT", 2, "班组已核对时间与产量口径"), 200)
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.availableActions[0]").value("APPROVE"));
		act(id, "oee-loop-approve-001", simpleAction("APPROVE", 3, "生产经理复核通过并冻结指标"), 200)
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.availableActions").isEmpty())
				.andExpect(jsonPath("$.events[0].action").value("APPROVED"));

		mockMvc.perform(get("/api/v1/equipment/oee-records?page=0&size=20&status=APPROVED")
					.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isOk())
				.andExpect(jsonPath("$.approvedRecordCount").value(2))
				.andExpect(jsonPath("$.averageOeeRate").value(81.9584))
				.andExpect(jsonPath("$.items[0].downtimes").isEmpty());
	}

	@Test
	void rejectsOverlapBoundsBadPerformanceAndStaleVersionsButReplaysRequest() throws Exception {
		String id = extractId(create("oee-validation-create-001", "2026-09-02T00:00:00Z", "2026-09-02T08:00:00Z",
				480, 60, 400, 390).andExpect(status().isCreated()).andReturn());
		act(id, "oee-validation-stop-001", addDowntime(0, "2026-09-02T01:00:00Z", "2026-09-02T01:30:00Z",
				"SETUP_CHANGEOVER", "生产组", "产品换型与首件调机"), 200);
		act(id, "oee-validation-overlap-001", addDowntime(1, "2026-09-02T01:20:00Z", "2026-09-02T01:40:00Z",
				"QUALITY_HOLD", "质量组", "首件质量确认等待"), 409);
		act(id, "oee-validation-outside-001", addDowntime(1, "2026-09-01T23:50:00Z", "2026-09-02T00:10:00Z",
				"OTHER", "生产组", "窗口外停机不应接受"), 422);
		act(id, "oee-validation-stale-001", simpleAction("SUBMIT", 0, "使用旧版本提交应被拒绝"), 409);
		act(id, "oee-validation-stop-001", addDowntime(0, "2026-09-02T01:00:00Z", "2026-09-02T01:30:00Z",
				"SETUP_CHANGEOVER", "生产组", "产品换型与首件调机"), 200).andExpect(jsonPath("$.version").value(1));

		String tooFast = extractId(create("oee-performance-create-001", "2026-09-03T00:00:00Z", "2026-09-03T08:00:00Z",
				480, 60, 600, 590).andExpect(status().isCreated()).andReturn());
		act(tooFast, "oee-performance-submit-001", simpleAction("SUBMIT", 0, "提交前校验性能率不得超限"), 422);
	}

	@Test
	@Transactional
	void keepsReadsAvailableButSeparatesMaintenanceAndApprovalRoles() throws Exception {
		String id = extractId(create("oee-role-create-001", "2026-09-04T00:00:00Z", "2026-09-04T08:00:00Z",
				480, 60, 400, 390).andExpect(status().isCreated()).andReturn());
		act(id, "oee-role-submit-001", simpleAction("SUBMIT", 0, "设备经理提交人工核实记录"), 200);
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'MAINTENANCE_MANAGER' where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)",
				"20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		mockMvc.perform(get("/api/v1/equipment/oee-records/{id}", id).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.availableActions").isEmpty());
		act(id, "oee-role-approve-001", simpleAction("APPROVE", 1, "设备经理不能代替生产审核"), 403);
	}

	private org.springframework.test.web.servlet.ResultActions create(String requestId, String start, String end,
			int planned, int ideal, int total, int good) throws Exception {
		String body = """
				{"assetId":"%s","windowStart":"%s","windowEnd":"%s","plannedProductionMinutes":%d,
				 "idealCycleSeconds":%d,"totalCount":%d,"goodCount":%d,"shiftName":"白班",
				 "productionReference":"PO-OEE-TEST","sourceReference":"班组核实表-001","reason":"建立人工核实 OEE 记录"}
				""".formatted(ASSET_ID, start, end, planned, ideal, total, good);
		return mockMvc.perform(post("/api/v1/equipment/oee-records").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private org.springframework.test.web.servlet.ResultActions act(String id, String requestId, String body, int code) throws Exception {
		return mockMvc.perform(post("/api/v1/equipment/oee-records/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().is(code));
	}

	private static String addDowntime(long version, String start, String end, String category, String party, String description) {
		return """
				{"action":"ADD_DOWNTIME","reason":"登记停机责任证据","expectedVersion":%d,
				 "downtimeStartedAt":"%s","downtimeEndedAt":"%s","reasonCategory":"%s",
				 "responsibleParty":"%s","description":"%s"}
				""".formatted(version, start, end, category, party, description);
	}

	private static String simpleAction(String action, long version, String reason) {
		return "{\"action\":\"%s\",\"reason\":\"%s\",\"expectedVersion\":%d}".formatted(action, reason, version);
	}

	private static String extractId(MvcResult result) throws Exception {
		String response = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(response);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + response);
		return matcher.group(1);
	}
}
