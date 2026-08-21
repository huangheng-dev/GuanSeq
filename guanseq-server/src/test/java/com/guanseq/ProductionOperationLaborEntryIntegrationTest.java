package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductionOperationLaborEntryIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String MAKE_MATERIAL = "42000000-0000-4000-8000-000000000001";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	ProductionOperationLaborEntryIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void recordsApprovesAndVoidsActualLaborWithIdempotencyAndConcurrency() throws Exception {
		String orderId = createAndReleaseOrder("labor-order-create-001", "labor-order-release-001");
		JsonNode tasks = read(getByOrder(orderId));
		String taskId = tasks.get(0).path("id").asText();
		startTask(taskId, "labor-task-start-001");

		String createBody = laborBody(taskId, LocalDate.now(), 95);
		MvcResult created = mockMvc.perform(post("/api/v1/production/operation-labor-entries")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-record-001")
				.contentType(MediaType.APPLICATION_JSON).content(createBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECORDED"))
				.andExpect(jsonPath("$.actualMinutes").value(95)).andExpect(jsonPath("$.version").value(0))
				.andReturn();
		String entryId = read(created).path("id").asText();

		mockMvc.perform(post("/api/v1/production/operation-labor-entries")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-record-001")
				.contentType(MediaType.APPLICATION_JSON).content(createBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(entryId));
		assertThat(eventCount(entryId)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/production/operation-labor-entries/{id}/actions", entryId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-approve-before-complete")
				.contentType(MediaType.APPLICATION_JSON).content(actionBody("APPROVE", 0, "班组长确认")))
				.andExpect(status().isUnprocessableEntity());

		completeTask(taskId, "labor-task-complete-001");
		mockMvc.perform(post("/api/v1/production/operation-labor-entries/{id}/actions", entryId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-approve-001")
				.contentType(MediaType.APPLICATION_JSON).content(actionBody("APPROVE", 0, "班组长确认")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(post("/api/v1/production/operation-labor-entries/{id}/actions", entryId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-approve-001")
				.contentType(MediaType.APPLICATION_JSON).content(actionBody("APPROVE", 0, "重复请求")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
		assertThat(eventCount(entryId)).isEqualTo(2);

		mockMvc.perform(post("/api/v1/production/operation-labor-entries/{id}/actions", entryId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-void-stale-version")
				.contentType(MediaType.APPLICATION_JSON).content(actionBody("VOID", 0, "录入错误")))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/production/operation-labor-entries/{id}/actions", entryId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-void-001")
				.contentType(MediaType.APPLICATION_JSON).content(actionBody("VOID", 1, "操作人归属错误，冲销后重录")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOIDED"))
				.andExpect(jsonPath("$.voidReason").value("操作人归属错误，冲销后重录"));

		mockMvc.perform(get("/api/v1/production/operation-labor-entries?taskId={taskId}", taskId)
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(entryId))
				.andExpect(jsonPath("$.items[0].events.length()").value(3));
		Number approvedMinutes = jdbcTemplate.queryForObject(
				"select coalesce(sum(actual_minutes), 0) from production.operation_labor_entries where task_id = cast(? as uuid) and status = 'APPROVED'",
				Number.class, taskId);
		assertThat(approvedMinutes.doubleValue()).isZero();
	}

	@Test
	@Transactional
	void rejectsInvalidDatesAndUnauthorizedRecording() throws Exception {
		String orderId = createAndReleaseOrder("labor-order-create-002", "labor-order-release-002");
		String taskId = read(getByOrder(orderId)).get(0).path("id").asText();

		mockMvc.perform(post("/api/v1/production/operation-labor-entries")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-before-start")
				.contentType(MediaType.APPLICATION_JSON).content(laborBody(taskId, LocalDate.now(), 30)))
				.andExpect(status().isUnprocessableEntity());

		startTask(taskId, "labor-task-start-002");
		mockMvc.perform(post("/api/v1/production/operation-labor-entries")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-future-date")
				.contentType(MediaType.APPLICATION_JSON).content(laborBody(taskId, LocalDate.now().plusDays(1), 30)))
				.andExpect(status().isUnprocessableEntity());

		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'SALES_CLERK' where id = '30000000-0000-4000-8000-000000000101'");
		try {
			mockMvc.perform(post("/api/v1/production/operation-labor-entries")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "labor-forbidden")
					.contentType(MediaType.APPLICATION_JSON).content(laborBody(taskId, LocalDate.now(), 30)))
					.andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where id = '30000000-0000-4000-8000-000000000101'");
		}
	}

	private String createAndReleaseOrder(String createRequestId, String releaseRequestId) throws Exception {
		LocalDate start = LocalDate.now();
		MvcResult created = mockMvc.perform(post("/api/v1/production/orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", createRequestId).contentType(MediaType.APPLICATION_JSON)
				.content(orderBody(start, start.plusDays(5))))
				.andExpect(status().isOk()).andReturn();
		String id = read(created).path("id").asText();
		mockMvc.perform(post("/api/v1/production/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", releaseRequestId).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"action":"RELEASE","expectedVersion":0,"comment":"实际人工工时切片测试下达"}
						"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
		return id;
	}

	private MvcResult getByOrder(String orderId) throws Exception {
		return mockMvc.perform(get("/api/v1/production/orders/{orderId}/operation-tasks", orderId)
				.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isOk()).andReturn();
	}

	private void startTask(String taskId, String requestId) throws Exception {
		mockMvc.perform(post("/api/v1/production/operation-tasks/{id}/actions", taskId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"action":"START","expectedVersion":0,"shiftName":"白班","operatorName":"陈磊"}
						"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	private void completeTask(String taskId, String requestId) throws Exception {
		mockMvc.perform(post("/api/v1/production/operation-tasks/{id}/actions", taskId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"action":"COMPLETE","expectedVersion":1,"completedQuantity":1,"shiftName":"白班","operatorName":"陈磊"}
						"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	private long eventCount(String entryId) {
		Long count = jdbcTemplate.queryForObject(
				"select count(*) from production.operation_labor_events where entry_id = cast(? as uuid)", Long.class, entryId);
		return count == null ? 0 : count;
	}

	private static String orderBody(LocalDate start, LocalDate receipt) {
		return """
				{"materialId":"%s","plannedQuantity":2,"plannedStartDate":"%s","plannedReceiptDate":"%s","workshop":"总装一车间","owner":"周启明","sourceType":"MANUAL","sourceId":null,"sourceNumber":null}
				""".formatted(MAKE_MATERIAL, start, receipt);
	}

	private static String laborBody(String taskId, LocalDate date, int minutes) {
		return """
				{"taskId":"%s","workDate":"%s","shiftName":"白班","operatorName":"陈磊","actualMinutes":%d,"note":"实际作业投入"}
				""".formatted(taskId, date, minutes);
	}

	private static String actionBody(String action, long version, String reason) {
		return """
				{"action":"%s","expectedVersion":%d,"reason":"%s"}
				""".formatted(action, version, reason);
	}

	private static JsonNode read(MvcResult result) throws Exception {
		return MAPPER.readTree(result.getResponse().getContentAsString());
	}
}
