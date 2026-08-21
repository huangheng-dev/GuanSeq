package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductRoutingIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String GS_800 = "42000000-0000-4000-8000-000000000001";
	private static final String PM_45 = "42000000-0000-4000-8000-000000000002";
	private static final String BR_6204 = "42000000-0000-4000-8000-000000000003";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	ProductRoutingIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void createsUpdatesPublishesAndProtectsControlledRoutingVersion() throws Exception {
		mockMvc.perform(get("/api/v1/product/routings?page=0&size=20&status=ALL")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.routingNumber == 'RTG-260815-001')]").exists())
				.andExpect(jsonPath("$.items[?(@.routingNumber == 'RTG-HIDDEN')]").doesNotExist());

		MvcResult created = mockMvc.perform(post("/api/v1/product/routings")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "routing-create-test-0001")
					.contentType(MediaType.APPLICATION_JSON).content(body(PM_45, "V9.TEST", false, 18)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.materialCode").value("PM-45"))
				.andExpect(jsonPath("$.operations[0].sequenceNumber").value(10))
				.andExpect(jsonPath("$.events[0].requestId").value("routing-create-test-0001"))
				.andReturn();
		String id = extractId(created);

		mockMvc.perform(put("/api/v1/product/routings/{id}", id).with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(updateBody(PM_45, "V9.TEST", 20, 99)))
				.andExpect(status().isConflict());
		mockMvc.perform(put("/api/v1/product/routings/{id}", id).with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(updateBody(PM_45, "V9.TEST", 20, 0)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.operations[0].runMinutesPerUnit").value(20));

		mockMvc.perform(post("/api/v1/product/routings/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "routing-publish-test-0001").contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"PUBLISH\",\"expectedVersion\":1}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.events[0].action").value("PUBLISHED"));

		mockMvc.perform(put("/api/v1/product/routings/{id}", id).with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(updateBody(PM_45, "V9.TEST", 22, 2)))
				.andExpect(status().isConflict());
	}

	@Test
	void rejectsBoughtMaterialDuplicateOperationsAndSecondPublishedVersion() throws Exception {
		mockMvc.perform(post("/api/v1/product/routings").with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(body(BR_6204, "V9.BUY", false, 10)))
				.andExpect(status().isUnprocessableEntity());
		mockMvc.perform(post("/api/v1/product/routings").with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(body(PM_45, "V9.DUP", true, 10)))
				.andExpect(status().isUnprocessableEntity());

		MvcResult second = mockMvc.perform(post("/api/v1/product/routings").with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON).content(body(GS_800, "V9.CONFLICT", false, 10)))
				.andExpect(status().isOk()).andReturn();
		mockMvc.perform(post("/api/v1/product/routings/{id}/actions", extractId(second))
					.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"PUBLISH\",\"expectedVersion\":0}"))
				.andExpect(status().isConflict());
	}

	@Test
	@Transactional
	void deniesMaintenanceForRoleOutsideProductBoundary() throws Exception {
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'VIEWER' where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)",
				"20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		try {
			mockMvc.perform(post("/api/v1/product/routings").with(httpBasic(USERNAME, PASSWORD))
						.contentType(MediaType.APPLICATION_JSON).content(body(PM_45, "V9.DENIED", false, 10)))
					.andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'PLANNING_MANAGER' where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)",
					"20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		}
	}

	private static String body(String materialId, String versionCode, boolean duplicate, int runMinutes) {
		String second = duplicate ? ",{\"operationCode\":\"OP-CUT\",\"operationName\":\"重复切削\",\"workCenterCode\":\"WC-02\",\"workCenterName\":\"二号中心\",\"setupMinutes\":2,\"runMinutesPerUnit\":3,\"queueMinutes\":0,\"inspectionRequired\":false}" : "";
		return """
				{"materialId":"%s","usageType":"PRODUCTION","versionCode":"%s","baseQuantity":1,
				 "effectiveFrom":"%s","owner":"顾工","changeReason":"集成测试受控路线",
				 "operations":[{"operationCode":"OP-CUT","operationName":"精密切削","workCenterCode":"WC-CNC-01","workCenterName":"数控中心","setupMinutes":10,"runMinutesPerUnit":%d,"queueMinutes":5,"inspectionRequired":true,"instructionSummary":"按图加工"}%s]}
				""".formatted(materialId, versionCode, LocalDate.now(), runMinutes, second);
	}

	private static String updateBody(String materialId, String versionCode, int runMinutes, long expectedVersion) {
		String create = body(materialId, versionCode, false, runMinutes).trim();
		return create.substring(0, create.length() - 1) + ",\"expectedVersion\":" + expectedVersion + "}";
	}

	private static String extractId(MvcResult result) throws Exception {
		String response = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(response);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + response);
		return matcher.group(1);
	}
}
