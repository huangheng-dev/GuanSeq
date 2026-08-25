package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class EquipmentAssetIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	EquipmentAssetIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void requiresAuthenticationAndScopesListAndDetailToCurrentWorkspace() throws Exception {
		mockMvc.perform(get("/api/v1/equipment/assets")).andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/equipment/assets?page=0&size=20&status=ALL&category=ALL")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.assetCode == 'EQ-CNC-001')]").exists())
				.andExpect(jsonPath("$.items[?(@.assetCode == 'EQ-WEST-HIDDEN')]").doesNotExist())
				.andExpect(jsonPath("$.items[?(@.assetCode == 'EQ-TENANT-HIDDEN')]").doesNotExist());

		mockMvc.perform(get("/api/v1/equipment/assets/{id}", "a1000000-0000-4000-8000-000000000098")
					.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/equipment/assets/{id}", "a1000000-0000-4000-8000-000000000099")
					.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isNotFound());
	}

	@Test
	void createsUpdatesAndProtectsUniquenessValidationAndOptimisticVersion() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/equipment/assets").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "equipment-create-contract-001")
					.contentType(MediaType.APPLICATION_JSON).content(createBody("EQ-INT-001", "集成测试设备")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.operatingStatus").value("IDLE"))
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.events[0].action").value("CREATED"))
				.andExpect(jsonPath("$.events[0].requestId").value("equipment-create-contract-001"))
				.andReturn();
		String id = extractId(created);

		mockMvc.perform(post("/api/v1/equipment/assets").with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(createBody("EQ-INT-001", "重复设备")))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/equipment/assets").with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(createBody("EQ-INT-BAD", "短原因").replace("集成测试建档原因", "短")))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/equipment/assets/{id}", id).with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(updateBody("更新后的设备", 99)))
				.andExpect(status().isConflict());
		mockMvc.perform(put("/api/v1/equipment/assets/{id}", id).with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "equipment-update-contract-001")
					.contentType(MediaType.APPLICATION_JSON).content(updateBody("更新后的设备", 0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.events[0].action").value("UPDATED"))
				.andExpect(jsonPath("$.events[0].reason").value("调整设备责任与位置"));
	}

	@Test
	void reportsBreakdownWithRepairOrderAndBlocksMaintenanceBypass() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/equipment/assets").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "equipment-life-create-001")
					.contentType(MediaType.APPLICATION_JSON).content(createBody("EQ-LIFE-001", "状态流转设备")))
				.andExpect(status().isCreated()).andReturn();
		String id = extractId(created);

		act(id, "STOP", "闲置设备不能直接停机", 0, "equipment-life-invalid-001", 409);
		act(id, "START", "白班生产任务开机", 0, "equipment-life-start-001", 200);
		mockMvc.perform(put("/api/v1/equipment/assets/{id}", id).with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(updateBody("运行中修改", 1)))
				.andExpect(status().isConflict());
		act(id, "REPORT_BREAKDOWN", "主轴振动异常，人工报故障", 1, "equipment-life-down-001", 200);
		act(id, "START_MAINTENANCE", "尝试绕过维修工单直接开工", 2, "equipment-life-maintain-001", 409);

		mockMvc.perform(get("/api/v1/equipment/assets/{id}", id).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.operatingStatus").value("DOWN"))
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.events[0].action").value("BREAKDOWN_REPORTED"))
				.andExpect(jsonPath("$.events[0].details.statusSource").value("MANUAL"))
				.andExpect(jsonPath("$.events[0].details.repairWorkOrderId").isNotEmpty())
				.andExpect(jsonPath("$.events[?(@.requestId == 'equipment-life-down-001')]").exists());

		mockMvc.perform(get("/api/v1/equipment/work-orders?type=REPAIR&query=EQ-LIFE-001&page=0&size=10")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].sourceType").value("BREAKDOWN"))
				.andExpect(jsonPath("$.items[0].status").value("PLANNED"));
	}

	@Test
	@Transactional
	void deniesWritesOutsideEquipmentBoundaryButKeepsReadsAvailable() throws Exception {
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'QUALITY_INSPECTOR' where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)",
				"20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		mockMvc.perform(get("/api/v1/equipment/assets?page=0&size=10").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/equipment/assets").with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(createBody("EQ-DENIED-001", "无权设备")))
				.andExpect(status().isForbidden());
	}

	private void act(String id, String action, String reason, long version, String requestId, int statusCode) throws Exception {
		mockMvc.perform(post("/api/v1/equipment/assets/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"%s\",\"reason\":\"%s\",\"expectedVersion\":%d}".formatted(action, reason, version)))
				.andExpect(status().is(statusCode));
	}

	private static String createBody(String code, String name) {
		return """
				{"assetCode":"%s","assetName":"%s","category":"PRODUCTION","manufacturer":"测试制造商",
				 "model":"TEST-100","serialNumber":"SN-100","workCenterCode":"WC-TEST","workCenterName":"测试中心",
				 "location":"测试车间 A-01","responsiblePerson":"测试责任人","commissioningDate":"2026-08-01","reason":"集成测试建档原因"}
				""".formatted(code, name);
	}

	private static String updateBody(String name, long version) {
		return """
				{"assetName":"%s","category":"PRODUCTION","manufacturer":"更新制造商","model":"TEST-200",
				 "serialNumber":"SN-200","workCenterCode":"WC-TEST","workCenterName":"测试中心",
				 "location":"测试车间 A-02","responsiblePerson":"新责任人","commissioningDate":"2026-08-02",
				 "reason":"调整设备责任与位置","expectedVersion":%d}
				""".formatted(name, version);
	}

	private static String extractId(MvcResult result) throws Exception {
		String response = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(response);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + response);
		return matcher.group(1);
	}
}
