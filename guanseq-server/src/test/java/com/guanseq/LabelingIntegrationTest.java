package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LabelingIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String TASK_ID = "93000000-0000-4000-8000-000000000001";
	private static final String HIDDEN_STOCK_ID = "73000000-0000-4000-8000-000000000099";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	LabelingIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void preparesInitialAndReprintEvidenceWithoutClaimingPhysicalPrint() throws Exception {
		JsonNode references = MAPPER.readTree(mockMvc.perform(get("/api/v1/labeling/reference-data")
				.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isOk())
				.andExpect(jsonPath("$.allowedObjectTypes.length()").value(3))
				.andExpect(jsonPath("$.templates[?(@.version == 'OT-V1')]").exists())
				.andExpect(jsonPath("$.candidates[?(@.payload == 'EMP:lin.hao')]").exists())
				.andExpect(jsonPath("$.candidates[?(@.payload == 'STOCK:73000000-0000-4000-8000-000000000001')]").exists())
				.andReturn().getResponse().getContentAsString());
		JsonNode task = findCandidate(references, "OPERATION_TASK", TASK_ID);

		String initialRequestId = "label-initial-task-0001";
		MvcResult initialResult = mockMvc.perform(post("/api/v1/labeling/print-requests")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", initialRequestId)
				.contentType(MediaType.APPLICATION_JSON).content(body(task, "INITIAL", 1, null)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.objectType").value("OPERATION_TASK"))
				.andExpect(jsonPath("$.payload").value("OT:OT-260815-900001"))
				.andExpect(jsonPath("$.templateVersion").value("OT-V1"))
				.andExpect(jsonPath("$.status").value("PREPARED"))
				.andExpect(jsonPath("$.reason").isEmpty()).andReturn();
		JsonNode initial = MAPPER.readTree(initialResult.getResponse().getContentAsString());

		mockMvc.perform(post("/api/v1/labeling/print-requests").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", initialRequestId).contentType(MediaType.APPLICATION_JSON)
				.content(body(task, "INITIAL", 1, null))).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(initial.path("id").asText()));
		assertThat(jdbcTemplate.queryForObject("select count(*) from labeling.print_requests where request_id = ?",
				Integer.class, initialRequestId)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/labeling/print-requests").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "label-initial-task-duplicate").contentType(MediaType.APPLICATION_JSON)
				.content(body(task, "INITIAL", 1, null))).andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/labeling/print-requests").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "label-reprint-task-short").contentType(MediaType.APPLICATION_JSON)
				.content(body(task, "REPRINT", 1, "破损"))).andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/labeling/print-requests").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "label-reprint-task-0001").contentType(MediaType.APPLICATION_JSON)
				.content(body(task, "REPRINT", 2, "现场标签污损无法识别")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("REPRINT"))
				.andExpect(jsonPath("$.copies").value(2)).andExpect(jsonPath("$.reason").value("现场标签污损无法识别"));

		assertThat(jdbcTemplate.queryForObject("select count(*) from labeling.print_requests where object_id = cast(? as uuid)",
				Integer.class, TASK_ID)).isEqualTo(2);
	}

	@Test
	@Transactional
	void rejectsStaleCrossTenantOtherEmployeeAndUnauthorizedRole() throws Exception {
		JsonNode references = MAPPER.readTree(mockMvc.perform(get("/api/v1/labeling/reference-data")
				.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		JsonNode task = findCandidate(references, "OPERATION_TASK", TASK_ID);
		String staleBody = """
				{"objectType":"OPERATION_TASK","objectId":"%s","expectedObjectVersion":%d,"mode":"INITIAL","copies":1}
				""".formatted(TASK_ID, task.path("version").asLong() + 1);
		mockMvc.perform(post("/api/v1/labeling/print-requests").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "label-stale-task").contentType(MediaType.APPLICATION_JSON).content(staleBody))
				.andExpect(status().isConflict());

		String hiddenStockBody = """
				{"objectType":"STOCK_BALANCE","objectId":"%s","expectedObjectVersion":0,"mode":"INITIAL","copies":1}
				""".formatted(HIDDEN_STOCK_ID);
		mockMvc.perform(post("/api/v1/labeling/print-requests").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "label-hidden-stock").contentType(MediaType.APPLICATION_JSON).content(hiddenStockBody))
				.andExpect(status().isNotFound());

		String otherEmployeeBody = """
				{"objectType":"EMPLOYEE","objectId":"99999999-9999-4999-8999-999999999997","expectedObjectVersion":0,"mode":"INITIAL","copies":1}
				""";
		mockMvc.perform(post("/api/v1/labeling/print-requests").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "label-other-employee").contentType(MediaType.APPLICATION_JSON).content(otherEmployeeBody))
				.andExpect(status().isForbidden());

		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'FINANCE_MANAGER' where id = cast(? as uuid)",
				"30000000-0000-4000-8000-000000000101");
		mockMvc.perform(get("/api/v1/labeling/reference-data").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isForbidden());
	}

	private static JsonNode findCandidate(JsonNode references, String type, String id) {
		for (JsonNode item : references.path("candidates"))
			if (type.equals(item.path("objectType").asText()) && id.equals(item.path("objectId").asText())) return item;
		throw new AssertionError("Label candidate not found: " + type + " " + id);
	}

	private static String body(JsonNode candidate, String mode, int copies, String reason) {
		String reasonPart = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
		return "{\"objectType\":\"" + candidate.path("objectType").asText() + "\",\"objectId\":\""
				+ candidate.path("objectId").asText() + "\",\"expectedObjectVersion\":" + candidate.path("version").asLong()
				+ ",\"mode\":\"" + mode + "\",\"copies\":" + copies + reasonPart + "}";
	}
}

