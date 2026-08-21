package com.guanseq;

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
class ProductionOperationTaskIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String MAKE_MATERIAL = "42000000-0000-4000-8000-000000000001";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	ProductionOperationTaskIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void releasesCreatesTasksExecutesAndGuardsWorkReport() throws Exception {
		String orderId = createAndReleaseOrder();
		mockMvc.perform(get("/api/v1/production/orders/{orderId}/operation-tasks", orderId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].sequenceNumber").value(10))
				.andExpect(jsonPath("$[0].status").value("PENDING"))
				.andExpect(jsonPath("$[2].inspectionRequired").value(true));

		JsonNode tasks = readArray(getByOrder(orderId));
		String firstId = tasks.get(0).path("id").asText();
		String secondId = tasks.get(1).path("id").asText();
		String thirdId = tasks.get(2).path("id").asText();

		startTask(firstId, 0, "operation-start-001");
		completeTask(firstId, 1, 2, "operation-complete-001");
		mockMvc.perform(get("/api/v1/production/orders/{id}", orderId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

		mockMvc.perform(post("/api/v1/production/work-reports").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "report-before-all-ops").contentType(MediaType.APPLICATION_JSON)
				.content(reportBody(orderId, 1, 2)))
				.andExpect(status().isConflict());

		long firstEventCount = eventCount(firstId);
		mockMvc.perform(post("/api/v1/production/operation-tasks/{id}/actions", firstId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "operation-start-001").contentType(MediaType.APPLICATION_JSON)
				.content(startBody(0)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
		mockMvc.perform(post("/api/v1/production/operation-tasks/{id}/actions", firstId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "operation-complete-001").contentType(MediaType.APPLICATION_JSON)
				.content(completeBody(2, 2)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.completedQuantity").value(2));
		org.assertj.core.api.Assertions.assertThat(eventCount(firstId)).isEqualTo(firstEventCount);

		startTask(secondId, 0, "operation-start-002");
		completeTask(secondId, 1, 2, "operation-complete-002");
		startTask(thirdId, 0, "operation-start-003");
		mockMvc.perform(post("/api/v1/production/operation-tasks/{id}/actions", thirdId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "operation-version-conflict").contentType(MediaType.APPLICATION_JSON)
				.content(completeBody(999, 2)))
				.andExpect(status().isConflict());
		completeTask(thirdId, 1, 2, "operation-complete-003");

		mockMvc.perform(post("/api/v1/production/work-reports").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "report-after-all-ops").contentType(MediaType.APPLICATION_JSON)
				.content(reportBody(orderId, 1, 2)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_INSPECTION"));
	}

	@Test
	@Transactional
	void rejectsActionForUnauthorizedRole() throws Exception {
		String orderId = createAndReleaseOrder();
		String taskId = readArray(getByOrder(orderId)).get(0).path("id").asText();
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'SALES_CLERK' where user_id = cast(? as uuid)", "20000000-0000-4000-8000-000000000001");
		mockMvc.perform(post("/api/v1/production/operation-tasks/{id}/actions", taskId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "operation-forbidden").contentType(MediaType.APPLICATION_JSON)
				.content(startBody(0)))
				.andExpect(status().isForbidden());
	}

	private String createAndReleaseOrder() throws Exception {
		LocalDate start = LocalDate.now().plusDays(1);
		MvcResult created = mockMvc.perform(post("/api/v1/production/orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "operation-order-create").contentType(MediaType.APPLICATION_JSON)
				.content(orderBody(start, start.plusDays(5))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
		String id = MAPPER.readTree(created.getResponse().getContentAsString()).path("id").asText();
		mockMvc.perform(post("/api/v1/production/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "operation-order-release").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"action":"RELEASE","expectedVersion":0,"comment":"工序执行切片测试下达"}
						"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"))
				.andExpect(jsonPath("$.version").value(1));
		return id;
	}

	private MvcResult getByOrder(String orderId) throws Exception {
		return mockMvc.perform(get("/api/v1/production/orders/{orderId}/operation-tasks", orderId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andReturn();
	}

	private void startTask(String taskId, long expectedVersion, String requestId) throws Exception {
		mockMvc.perform(post("/api/v1/production/operation-tasks/{id}/actions", taskId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON).content(startBody(expectedVersion)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.events[?(@.action=='START')].requestId").value(org.hamcrest.Matchers.hasItem(requestId)));
	}

	private void completeTask(String taskId, long expectedVersion, int quantity, String requestId) throws Exception {
		mockMvc.perform(post("/api/v1/production/operation-tasks/{id}/actions", taskId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
				.content(completeBody(expectedVersion, quantity)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.completedQuantity").value(quantity));
	}

	private long eventCount(String taskId) {
		Long count = jdbcTemplate.queryForObject("select count(*) from production.operation_events where task_id = cast(? as uuid)", Long.class, taskId);
		return count == null ? 0 : count;
	}

	private JsonNode readArray(MvcResult result) throws Exception {
		return MAPPER.readTree(result.getResponse().getContentAsString());
	}

	private static String orderBody(LocalDate start, LocalDate receipt) {
		return """
				{"materialId":"%s","plannedQuantity":8,"plannedStartDate":"%s","plannedReceiptDate":"%s","workshop":"总装一车间","owner":"周启明","sourceType":"MANUAL","sourceId":null,"sourceNumber":null}
				""".formatted(MAKE_MATERIAL, start, receipt);
	}

	private static String startBody(long expectedVersion) {
		return """
				{"action":"START","expectedVersion":%d,"shiftName":"白班","operatorName":"陈磊","note":"按工艺开工"}
				""".formatted(expectedVersion);
	}

	private static String completeBody(long expectedVersion, int quantity) {
		return """
				{"action":"COMPLETE","expectedVersion":%d,"completedQuantity":%d,"shiftName":"白班","operatorName":"陈磊","note":"工序完工"}
				""".formatted(expectedVersion, quantity);
	}

	private static String reportBody(String orderId, int quantity, long expectedOrderVersion) {
		return """
				{"orderId":"%s","quantity":%d,"shiftName":"白班","operatorName":"陈磊","note":"全部工序完成后报工","expectedOrderVersion":%d}
				""".formatted(orderId, quantity, expectedOrderVersion);
	}
}

