package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class EquipmentMaintenanceIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	EquipmentMaintenanceIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void requiresAuthenticationAndScopesWorkOrdersToCurrentWorkspace() throws Exception {
		mockMvc.perform(get("/api/v1/equipment/work-orders")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/equipment/work-orders?page=0&size=20&type=ALL&status=ALL")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.workOrderNumber == 'INSP-20260825-0001')]").exists())
				.andExpect(jsonPath("$.items[?(@.workOrderNumber == 'INSP-HIDDEN-WEST')]").doesNotExist())
				.andExpect(jsonPath("$.items[?(@.workOrderNumber == 'WO-HIDDEN-TENANT')]").doesNotExist());
		mockMvc.perform(get("/api/v1/equipment/work-orders/{id}", "b1000000-0000-4000-8000-000000000098")
					.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/equipment/work-orders/{id}", "b1000000-0000-4000-8000-000000000099")
					.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isNotFound());
	}

	@Test
	void closesInspectionFailureThroughRepairReworkAndAcceptance() throws Exception {
		JsonNode asset = createAsset("EQ-MAINT-FLOW-001");
		JsonNode inspection = createWorkOrder(asset, "INSPECTION", "闭环点检任务", "maintenance-flow-create-inspection");
		String inspectionId = inspection.path("id").asText();

		JsonNode startedInspection = act(inspectionId, "START", "现场人员开始执行点检", 0, 0, null, null,
				"maintenance-flow-start-inspection", 200);
		org.assertj.core.api.Assertions.assertThat(startedInspection.path("status").asText()).isEqualTo("IN_PROGRESS");

		JsonNode failedInspection = act(inspectionId, "COMPLETE", "点检发现主轴温升异常", 1, 0, "FAIL",
				"空载运行十分钟后温升超过控制限", "maintenance-flow-fail-inspection", 200);
		org.assertj.core.api.Assertions.assertThat(failedInspection.path("status").asText()).isEqualTo("COMPLETED");
		org.assertj.core.api.Assertions.assertThat(failedInspection.path("assetOperatingStatus").asText()).isEqualTo("DOWN");
		org.assertj.core.api.Assertions.assertThat(failedInspection.path("events").get(0).path("details").path("repairWorkOrderId").asText()).isNotBlank();

		MvcResult repairResult = mockMvc.perform(get("/api/v1/equipment/work-orders?type=REPAIR&query=EQ-MAINT-FLOW-001&page=0&size=10")
					.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].sourceType").value("INSPECTION_FAILURE"))
				.andReturn();
		JsonNode repair = json(repairResult).path("items").get(0);
		String repairId = repair.path("id").asText();

		JsonNode repairStarted = act(repairId, "START", "维修人员接单并开始排查", 0, 1, null, null,
				"maintenance-flow-start-repair", 200);
		org.assertj.core.api.Assertions.assertThat(repairStarted.path("assetOperatingStatus").asText()).isEqualTo("MAINTENANCE");

		act(repairId, "SUBMIT_FOR_ACCEPTANCE", "维修完成并提交现场验收", 1, 2, null,
				"更换温度传感器并完成三轮空载验证", "maintenance-flow-submit-repair-1", 200);
		act(repairId, "REJECT", "第二轮负载测试仍有温度漂移", 2, 2, null, null,
				"maintenance-flow-reject-repair", 200);
		act(repairId, "SUBMIT_FOR_ACCEPTANCE", "完成返修并再次提交验收", 3, 2, null,
				"重新压接传感器端子并完成八小时负载验证", "maintenance-flow-submit-repair-2", 200);
		JsonNode accepted = act(repairId, "ACCEPT", "生产与设备共同确认运行稳定", 4, 2, null, null,
				"maintenance-flow-accept-repair", 200);
		org.assertj.core.api.Assertions.assertThat(accepted.path("status").asText()).isEqualTo("COMPLETED");
		org.assertj.core.api.Assertions.assertThat(accepted.path("outcome").asText()).isEqualTo("PASS");
		org.assertj.core.api.Assertions.assertThat(accepted.path("assetOperatingStatus").asText()).isEqualTo("IDLE");
		org.assertj.core.api.Assertions.assertThat(accepted.path("events").toString())
				.contains("REJECTED", "SUBMITTED_FOR_ACCEPTANCE", "ACCEPTED", "maintenance-flow-accept-repair");
	}

	@Test
	void completesPreventiveMaintenanceAndRejectsInvalidOrStaleCommands() throws Exception {
		JsonNode asset = createAsset("EQ-MAINT-PM-001");
		JsonNode order = createWorkOrder(asset, "PREVENTIVE_MAINTENANCE", "预防保养任务", "maintenance-pm-create");
		String id = order.path("id").asText();
		JsonNode started = act(id, "START", "按停机窗口开始预防保养", 0, 0, null, null,
				"maintenance-pm-start", 200);
		org.assertj.core.api.Assertions.assertThat(started.path("assetOperatingStatus").asText()).isEqualTo("MAINTENANCE");
		act(id, "COMPLETE", "保养项目完成并确认结果", 0, 1, "PASS", "滤芯、润滑和互锁项目全部通过",
				"maintenance-pm-stale", 409);
		JsonNode completed = act(id, "COMPLETE", "保养项目完成并确认结果", 1, 1, "PASS",
				"滤芯、润滑和互锁项目全部通过", "maintenance-pm-complete", 200);
		org.assertj.core.api.Assertions.assertThat(completed.path("assetOperatingStatus").asText()).isEqualTo("IDLE");
		org.assertj.core.api.Assertions.assertThat(completed.path("outcome").asText()).isEqualTo("PASS");

		JsonNode scheduleAsset = createAsset("EQ-MAINT-SCHEDULE-001");
		mockMvc.perform(post("/api/v1/equipment/work-orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "maintenance-invalid-schedule")
				.contentType(MediaType.APPLICATION_JSON)
				.content(workOrderBody(scheduleAsset, "INSPECTION", "无效计划", "2026-09-02T08:00:00Z", "2026-09-01T08:00:00Z")))
				.andExpect(status().isUnprocessableEntity());
		mockMvc.perform(post("/api/v1/equipment/work-orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "maintenance-missing-asset-version")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"assetId":"%s","workType":"INSPECTION","title":"缺失版本",
						 "description":"验证创建请求必须携带设备版本","priority":"LOW",
						 "plannedStartAt":"2026-09-01T08:00:00Z","dueAt":"2026-09-01T10:00:00Z",
						 "assignee":"设备测试员","reason":"验证缺失设备版本"}
						""".formatted(scheduleAsset.path("id").asText())))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/equipment/work-orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "maintenance-missing-action-versions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"CANCEL\",\"reason\":\"验证动作请求必须携带版本\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@Transactional
	void keepsReadsAvailableButDeniesWritesWithoutMaintenanceRole() throws Exception {
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'QUALITY_INSPECTOR' where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)",
				"20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		mockMvc.perform(get("/api/v1/equipment/work-orders?page=0&size=10").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.canMaintain").value(false));
		mockMvc.perform(post("/api/v1/equipment/work-orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "maintenance-denied-create")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"assetId":"a1000000-0000-4000-8000-000000000002","workType":"INSPECTION",
						 "title":"无权点检","description":"无权用户不能创建点检任务","priority":"LOW",
						 "plannedStartAt":"2026-09-01T08:00:00Z","dueAt":"2026-09-01T10:00:00Z",
						 "assignee":"测试人员","reason":"验证后端权限拒绝","assetExpectedVersion":0}
						"""))
				.andExpect(status().isForbidden());
	}

	private JsonNode createAsset(String code) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/assets").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "maintenance-create-asset-" + code)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"assetCode":"%s","assetName":"运维闭环测试设备","category":"PRODUCTION",
						 "location":"闭环测试区 A-01","responsiblePerson":"设备测试员","reason":"建立运维闭环测试设备"}
						""".formatted(code)))
				.andExpect(status().isCreated()).andReturn();
		return json(result);
	}

	private JsonNode createWorkOrder(JsonNode asset, String type, String title, String requestId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/work-orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
				.content(workOrderBody(asset, type, title, "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PLANNED"))
				.andExpect(jsonPath("$.events[0].requestId").value(requestId)).andReturn();
		return json(result);
	}

	private String workOrderBody(JsonNode asset, String type, String title, String start, String due) {
		return """
				{"assetId":"%s","workType":"%s","title":"%s","description":"执行设备运维闭环验证项目",
				 "priority":"HIGH","plannedStartAt":"%s","dueAt":"%s","assignee":"设备测试员",
				 "reason":"创建受控设备运维任务","assetExpectedVersion":%d}
				""".formatted(asset.path("id").asText(), type, title, start, due, asset.path("version").asLong());
	}

	private JsonNode act(String id, String action, String reason, long version, long assetVersion, String outcome,
			String notes, String requestId, int statusCode) throws Exception {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("action", action);
		payload.put("reason", reason);
		payload.put("expectedVersion", version);
		payload.put("assetExpectedVersion", assetVersion);
		if (outcome != null) payload.put("outcome", outcome);
		if (notes != null) payload.put("completionNotes", notes);
		String body = MAPPER.writeValueAsString(payload);
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/work-orders/{id}/actions", id)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().is(statusCode)).andReturn();
		return statusCode == 200 ? json(result) : MAPPER.createObjectNode();
	}

	private JsonNode json(MvcResult result) throws Exception {
		return MAPPER.readTree(result.getResponse().getContentAsString());
	}
}
