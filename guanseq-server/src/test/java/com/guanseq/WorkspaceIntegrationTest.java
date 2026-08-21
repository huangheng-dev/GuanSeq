package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class WorkspaceIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String SOUTH_WORKSPACE = "10000000-0000-4000-8000-000000000102";
	private static final String RESTRICTED_WORKSPACE = "10000000-0000-4000-8000-000000000104";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	WorkspaceIntegrationTest(
			@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void protectsListsSwitchesAndAuditsAccessibleWorkspaces() throws Exception {
		mockMvc.perform(get("/api/v1/me/workspaces"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/me/workspaces")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "workspace-list-0001"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "workspace-list-0001"))
				.andExpect(jsonPath("$.username").value(USERNAME))
				.andExpect(jsonPath("$.currentWorkspaceId").value("10000000-0000-4000-8000-000000000101"))
				.andExpect(jsonPath("$.selectionVersion").value(0))
				.andExpect(jsonPath("$.workspaces.length()").value(3))
				.andExpect(jsonPath("$.workspaces[?(@.id == '%s')]", RESTRICTED_WORKSPACE).doesNotExist());

		mockMvc.perform(put("/api/v1/me/current-workspace")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "workspace-switch-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"workspaceId":"%s","expectedVersion":0}
							""".formatted(SOUTH_WORKSPACE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentWorkspaceId").value(SOUTH_WORKSPACE))
				.andExpect(jsonPath("$.selectionVersion").value(1))
				.andExpect(jsonPath("$.workspaces[?(@.id == '%s')].current", SOUTH_WORKSPACE).value(true));

		mockMvc.perform(put("/api/v1/me/current-workspace")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"workspaceId":"10000000-0000-4000-8000-000000000103","expectedVersion":0}
							"""))
				.andExpect(status().isConflict());

		mockMvc.perform(put("/api/v1/me/current-workspace")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"workspaceId":"%s","expectedVersion":1}
							""".formatted(RESTRICTED_WORKSPACE)))
				.andExpect(status().isForbidden());

		Integer auditCount = jdbcTemplate.queryForObject(
				"""
				select count(*) from identity.audit_events
				where event_type = 'WORKSPACE_SWITCHED'
				and request_id = 'workspace-switch-0001'
				""",
				Integer.class);
		assertThat(auditCount).isEqualTo(1);
	}
}
