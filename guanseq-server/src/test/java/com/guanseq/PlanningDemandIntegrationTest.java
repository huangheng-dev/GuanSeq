package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PlanningDemandIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String MATERIAL = "42000000-0000-4000-8000-000000000002";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	PlanningDemandIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void exposesTenantScopedDemandsReferencesAndMrpInputs() throws Exception {
		mockMvc.perform(get("/api/v1/planning/independent-demands").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.demandNumber == 'DMD-260815-001')]").exists())
				.andExpect(jsonPath("$.items[?(@.demandNumber == 'DMD-HIDDEN')]").doesNotExist());

		mockMvc.perform(get("/api/v1/planning/mrp-inputs").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.demandNumber == 'DMD-260815-001')]").exists())
				.andExpect(jsonPath("$.items[?(@.demandNumber == 'DMD-260815-003')]").exists())
				.andExpect(jsonPath("$.items[?(@.demandNumber == 'DMD-260815-002')]").doesNotExist());

		mockMvc.perform(get("/api/v1/planning/demand-reference-data").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.materials[?(@.code == 'PM-45')]").exists())
				.andExpect(jsonPath("$.materials[?(@.code == 'PK-GS800')]").exists())
				.andExpect(jsonPath("$.materials[?(@.code == 'MAT-HIDDEN')]").doesNotExist());
	}

	@Test
	void createsUpdatesActivatesCancelsAndAuditsManualDemand() throws Exception {
		LocalDate requiredDate = LocalDate.now().plusDays(15);
		MvcResult created = mockMvc.perform(post("/api/v1/planning/independent-demands")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "demand-create-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody(requiredDate, "40", "HIGH")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sourceType").value("MANUAL"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.version").value(0))
				.andReturn();
		String demandId = extractId(created);

		mockMvc.perform(put("/api/v1/planning/independent-demands/{id}", demandId)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody(requiredDate.plusDays(1), "55", "URGENT", 0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(55))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(put("/api/v1/planning/independent-demands/{id}", demandId)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody(requiredDate.plusDays(1), "55", "URGENT", 0)))
				.andExpect(status().isConflict());

		performAction(demandId, "ACTIVATE", 1, null, "ACTIVE", 2);

		mockMvc.perform(post("/api/v1/planning/independent-demands/{id}/actions", demandId)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"CANCEL\",\"expectedVersion\":2}"))
				.andExpect(status().isBadRequest());

		performAction(demandId, "CANCEL", 2, "试制计划取消", "CANCELLED", 3);

		Integer auditCount = jdbcTemplate.queryForObject(
				"select count(*) from planning.demand_events where demand_id = cast(? as uuid)", Integer.class, demandId);
		assertThat(auditCount).isEqualTo(4);
	}

	@Test
	void preventsManualChangesToSalesOrderDemand() throws Exception {
		LocalDate requiredDate = LocalDate.now().plusDays(12);
		mockMvc.perform(put("/api/v1/planning/independent-demands/53000000-0000-4000-8000-000000000001")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody(requiredDate, "8", "NORMAL", 0)))
				.andExpect(status().isConflict());
	}

	private void performAction(String id, String action, long version, String comment, String expectedStatus, long expectedVersion) throws Exception {
		String commentJson = comment == null ? "null" : "\"" + comment + "\"";
		mockMvc.perform(post("/api/v1/planning/independent-demands/{id}/actions", id)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"%s\",\"expectedVersion\":%d,\"comment\":%s}".formatted(action, version, commentJson)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(expectedStatus))
				.andExpect(jsonPath("$.version").value(expectedVersion));
	}

	private static String createBody(LocalDate date, String quantity, String priority) {
		return """
				{"materialId":"%s","quantity":%s,"requiredDate":"%s","priority":"%s","owner":"林浩","note":"计划试制需求"}
				""".formatted(MATERIAL, quantity, date, priority);
	}

	private static String updateBody(LocalDate date, String quantity, String priority, long version) {
		return createBody(date, quantity, priority).trim().replaceFirst("}$", ",\"expectedVersion\":" + version + "}");
	}

	private static String extractId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(body);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + body);
		return matcher.group(1);
	}
}
