package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
@SpringBootTest
class WorkCenterCostRateIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	WorkCenterCostRateIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void publishesListsAndDeactivatesEffectiveRateIdempotently() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String code = "WC-QA-" + suffix;
		String requestId = "rate-create-" + suffix;
		MvcResult created = mockMvc.perform(post("/api/v1/finance/work-center-cost-rates")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON).content(createBody(code, LocalDate.now())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workCenterCode").value(code))
				.andExpect(jsonPath("$.laborRatePerHour").value(72.50))
				.andExpect(jsonPath("$.overheadRatePerHour").value(37.50))
				.andExpect(jsonPath("$.totalRatePerHour").value(110.00))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andReturn();
		String id = field(created, "id");

		mockMvc.perform(post("/api/v1/finance/work-center-cost-rates").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON).content(createBody(code, LocalDate.now())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

		mockMvc.perform(get("/api/v1/finance/work-center-cost-rates?query={code}&status=ACTIVE", code)
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));

		String statusRequestId = "rate-status-" + suffix;
		mockMvc.perform(post("/api/v1/finance/work-center-cost-rates/{id}/status", id)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", statusRequestId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\",\"expectedVersion\":0}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"))
				.andExpect(jsonPath("$.version").value(1));
		mockMvc.perform(post("/api/v1/finance/work-center-cost-rates/{id}/status", id)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", statusRequestId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\",\"expectedVersion\":0}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	@Transactional
	void rejectsDuplicateEffectiveDateAndUnauthorizedWrite() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String code = "WC-DUP-" + suffix;
		mockMvc.perform(post("/api/v1/finance/work-center-cost-rates").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "rate-first-" + suffix).contentType(MediaType.APPLICATION_JSON)
				.content(createBody(code, LocalDate.now()))).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/finance/work-center-cost-rates").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "rate-duplicate-" + suffix).contentType(MediaType.APPLICATION_JSON)
				.content(createBody(code, LocalDate.now()))).andExpect(status().isConflict());

		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'PRODUCTION_OPERATOR' where id = '30000000-0000-4000-8000-000000000101'");
		try {
			mockMvc.perform(post("/api/v1/finance/work-center-cost-rates").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "rate-denied-" + suffix).contentType(MediaType.APPLICATION_JSON)
					.content(createBody("WC-DENIED-" + suffix, LocalDate.now()))).andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where id = '30000000-0000-4000-8000-000000000101'");
		}
	}

	private static String createBody(String code, LocalDate effectiveDate) {
		return """
				{"workCenterCode":"%s","workCenterName":"测试工作中心","currency":"CNY","laborRatePerHour":72.5,"overheadRatePerHour":37.5,"effectiveDate":"%s"}
				""".formatted(code, effectiveDate);
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(name) + "\\\":\\\"([^\\\"]+)\\\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}
}
