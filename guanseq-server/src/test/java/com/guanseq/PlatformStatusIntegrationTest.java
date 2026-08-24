package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PlatformStatusIntegrationTest {

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	PlatformStatusIntegrationTest(
			@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void migratesSchemasAndExposesTrackedStatus() throws Exception {
		Integer schemaCount = jdbcTemplate.queryForObject(
				"select count(*) from information_schema.schemata where schema_name in ('platform', 'identity', 'masterdata', 'sales')",
				Integer.class);
		assertThat(schemaCount).isEqualTo(4);

		mockMvc.perform(get("/api/v1/platform/status").header("X-Request-Id", "test-request-0001"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "test-request-0001"))
				.andExpect(jsonPath("$.service").value("guanseq-server"))
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void returnsStableTrackedErrorsForAuthenticationAndValidation() throws Exception {
		mockMvc.perform(post("/api/v1/bootstrap/initial-workspace")
					.header("X-Request-Id", "bootstrap-disabled-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.requestId").value("bootstrap-disabled-0001"));

		mockMvc.perform(get("/api/v1/me/workspaces").header("X-Request-Id", "error-auth-0001"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("X-Request-Id", "error-auth-0001"))
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("请先完成身份认证"))
				.andExpect(jsonPath("$.requestId").value("error-auth-0001"))
				.andExpect(jsonPath("$.fieldErrors").isArray());

		mockMvc.perform(put("/api/v1/me/current-workspace")
					.with(httpBasic("lin.hao", "guanseq_dev"))
					.header("X-Request-Id", "error-validation-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"workspaceId":"10000000-0000-4000-8000-000000000101","expectedVersion":-1}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.requestId").value("error-validation-0001"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("expectedVersion"));
	}
}
