package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
}
