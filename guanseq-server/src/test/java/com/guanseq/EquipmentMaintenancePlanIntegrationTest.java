package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class EquipmentMaintenancePlanIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String ASSET_ID = "a1000000-0000-4000-8000-000000000002";
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	EquipmentMaintenancePlanIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void requiresAuthenticationAndScopesPlansToCurrentWorkspace() throws Exception {
		mockMvc.perform(get("/api/v1/equipment/maintenance-plans")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/equipment/maintenance-plans?page=0&size=20")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.planCode == 'PLAN-CNC-WEEKLY')]").exists())
				.andExpect(jsonPath("$.canMaintain").value(true));
		mockMvc.perform(get("/api/v1/equipment/maintenance-plans/{id}", "d1000000-0000-4000-8000-000000000099")
					.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void createsTemplateGeneratesDueOrderIdempotentlyAndExposesOverdueEvidence() throws Exception {
		JsonNode plan = createPlan("PLAN-OVERDUE-EVIDENCE", "2026-08-20", 365,
				"plan-overdue-create");
		String planId = plan.path("id").asText();
		assertThat(plan.path("nextGenerationDate").asText()).isEqualTo("2026-08-18");

		JsonNode first = generate("2026-08-25", "补生成试点设备到期任务", "plan-overdue-generate", 200);
		assertThat(first.path("items").toString()).contains(planId, "GENERATED");
		String workOrderId = jdbcTemplate.queryForObject("""
				select id::text from equipment.maintenance_work_orders
				where source_plan_id = cast(? as uuid) and source_due_date = date '2026-08-20'
				""", String.class, planId);
		assertThat(workOrderId).isNotBlank();
		assertThat(jdbcTemplate.queryForObject("""
				select work_order_number from equipment.maintenance_work_orders where id = cast(? as uuid)
				""", String.class, workOrderId)).matches("PM-\\d{8}-[A-F0-9]{8}");
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from equipment.maintenance_work_orders
				where source_plan_id = cast(? as uuid) and source_due_date = date '2026-08-20'
				""", Integer.class, planId)).isEqualTo(1);

		JsonNode replay = generate("2026-08-25", "补生成试点设备到期任务", "plan-overdue-generate", 200);
		assertThat(replay.path("id").asText()).isEqualTo(first.path("id").asText());
		generate("2026-08-25", "再次核对到期任务未重复生成", "plan-overdue-generate-again", 200);
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from equipment.maintenance_work_orders
				where source_plan_id = cast(? as uuid) and source_due_date = date '2026-08-20'
				""", Integer.class, planId)).isEqualTo(1);

		MvcResult detailResult = mockMvc.perform(get("/api/v1/equipment/maintenance-plans/{id}", planId)
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nextDueDate").value("2027-08-20"))
				.andExpect(jsonPath("$.overdueWorkOrderCount").value(1))
				.andExpect(jsonPath("$.overdueWorkOrderNumbers[0]").isNotEmpty()).andReturn();
		JsonNode detail = json(detailResult);
		mockMvc.perform(post("/api/v1/equipment/maintenance-plans/{id}/actions", planId)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "plan-overdue-inactivate")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"INACTIVATE\",\"reason\":\"停用旧周期但保留逾期责任\",\"expectedVersion\":%d}"
							.formatted(detail.path("version").asInt())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
		Integer workspaceOverdue = jdbcTemplate.queryForObject("""
				select count(*) from equipment.maintenance_work_orders
				where tenant_organization_id = '00000000-0000-4000-8000-000000000001'
				  and workspace_id = '10000000-0000-4000-8000-000000000101'
				  and source_plan_id is not null and status not in ('COMPLETED', 'CANCELLED') and due_at < now()
				""", Integer.class);
		mockMvc.perform(get("/api/v1/equipment/maintenance-plans?page=0&size=20")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.overdueWorkOrderCount").value(workspaceOverdue));
		mockMvc.perform(get("/api/v1/equipment/work-orders/{id}", workOrderId)
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sourceType").value("MAINTENANCE_PLAN"))
				.andExpect(jsonPath("$.sourcePlanId").value(planId))
				.andExpect(jsonPath("$.sourceDueDate").value("2026-08-20"))
				.andExpect(jsonPath("$.events[0].requestId").value("plan-overdue-generate"));
	}

	@Test
	@Transactional
	void controlsTemplateStatusWithVersionAndRejectsDuplicateCode() throws Exception {
		JsonNode plan = createPlan("PLAN-STATUS-CONTROL", "2026-09-10", 30, "plan-status-create");
		String id = plan.path("id").asText();
		mockMvc.perform(post("/api/v1/equipment/maintenance-plans/{id}/actions", id)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "plan-status-stale")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"INACTIVATE\",\"reason\":\"验证过期版本冲突\",\"expectedVersion\":9}"))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/equipment/maintenance-plans/{id}/actions", id)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "plan-status-inactivate")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"INACTIVATE\",\"reason\":\"试点设备停用周期模板\",\"expectedVersion\":0}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"))
				.andExpect(jsonPath("$.version").value(1));
		mockMvc.perform(post("/api/v1/equipment/maintenance-plans").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "plan-status-duplicate-code").contentType(MediaType.APPLICATION_JSON)
					.content(planBody("PLAN-STATUS-CONTROL", "2026-10-10", 30)))
				.andExpect(status().isConflict());
	}

	@Test
	@Transactional
	void keepsReadsAvailableButDeniesWritesWithoutMaintenanceRole() throws Exception {
		jdbcTemplate.update("""
				update identity.workspace_memberships set role_code = 'QUALITY_INSPECTOR'
				where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)
				""", "20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		mockMvc.perform(get("/api/v1/equipment/maintenance-plans").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.canMaintain").value(false));
		mockMvc.perform(post("/api/v1/equipment/maintenance-plans/generate").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "plan-denied-generate").contentType(MediaType.APPLICATION_JSON)
					.content("{\"asOfDate\":\"2026-08-25\",\"reason\":\"验证无权生成任务\"}"))
				.andExpect(status().isForbidden());
	}

	private JsonNode createPlan(String code, String firstDueDate, int intervalDays, String requestId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/maintenance-plans").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
					.content(planBody(code, firstDueDate, intervalDays)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.events[0].requestId").value(requestId)).andReturn();
		return json(result);
	}

	private String planBody(String code, String firstDueDate, int intervalDays) {
		return """
				{"planCode":"%s","name":"装配工位周期保养","workType":"PREVENTIVE_MAINTENANCE",
				 "assetId":"%s","description":"清洁气路并检查夹具定位与安全互锁",
				 "priority":"MEDIUM","intervalDays":%d,"leadDays":2,"firstDueDate":"%s",
				 "plannedStartTime":"08:30:00","dueTime":"11:30:00","assignee":"陈磊",
				 "reason":"建立受控周期维护模板","assetExpectedVersion":0}
				""".formatted(code, ASSET_ID, intervalDays, firstDueDate);
	}

	private JsonNode generate(String asOfDate, String reason, String requestId, int statusCode) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/maintenance-plans/generate")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"asOfDate\":\"%s\",\"reason\":\"%s\"}".formatted(asOfDate, reason)))
				.andExpect(status().is(statusCode)).andReturn();
		return statusCode == 200 ? json(result) : MAPPER.createObjectNode();
	}

	private JsonNode json(MvcResult result) throws Exception {
		return MAPPER.readTree(result.getResponse().getContentAsString());
	}
}
