package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
class WorkspaceAuditIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String USER_ID = "20000000-0000-4000-8000-000000000001";
	private static final String WORKSPACE_ID = "10000000-0000-4000-8000-000000000101";
	private static final String OTHER_WORKSPACE_ID = "10000000-0000-4000-8000-000000000102";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	WorkspaceAuditIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@BeforeEach
	void restoreAdministratorAndKnownWorkspace() {
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where user_id = ?::uuid and workspace_id = ?::uuid",
				USER_ID, WORKSPACE_ID);
		jdbcTemplate.update("update identity.user_workspace_preferences set current_workspace_id = ?::uuid where user_id = ?::uuid",
				WORKSPACE_ID, USER_ID);
	}

	@Test
	void queriesCurrentWorkspaceAuditWithFiltersIsolationAndNoRecursiveWrite() throws Exception {
		String visibleId = UUID.randomUUID().toString();
		String hiddenId = UUID.randomUUID().toString();
		insert(visibleId, WORKSPACE_ID, "ORGANIZATION_SITE_CREATED", "ORGANIZATION_UNIT", "SITE-AUDIT-001",
				"audit-query-visible-0001", Instant.now().minus(2, ChronoUnit.MINUTES));
		insert(hiddenId, OTHER_WORKSPACE_ID, "ORGANIZATION_SITE_CREATED", "ORGANIZATION_UNIT", "SITE-HIDDEN-001",
				"audit-query-hidden-0001", Instant.now().minus(1, ChronoUnit.MINUTES));

		mockMvc.perform(get("/api/v1/identity/audit-events"))
				.andExpect(status().isUnauthorized());

		Long before = countWorkspaceEvents();
		mockMvc.perform(get("/api/v1/identity/audit-events")
					.param("eventType", "ORGANIZATION_SITE_CREATED")
					.param("objectType", "ORGANIZATION_UNIT")
					.param("actorId", USER_ID)
					.param("query", "audit-query-visible")
					.param("page", "0").param("size", "10")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "audit-list-0001"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "audit-list-0001"))
				.andExpect(jsonPath("$.workspaceId").value(WORKSPACE_ID))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].id").value(visibleId))
				.andExpect(jsonPath("$.items[0].actorDisplayName").value("林浩"))
				.andExpect(jsonPath("$.items[0].requestId").value("audit-query-visible-0001"))
				.andExpect(jsonPath("$.eventTypes", org.hamcrest.Matchers.hasItem("ORGANIZATION_SITE_CREATED")))
				.andExpect(jsonPath("$.objectTypes", org.hamcrest.Matchers.hasItem("ORGANIZATION_UNIT")))
				.andExpect(jsonPath("$.actors[?(@.id == '" + USER_ID + "')].username").value("lin.hao"));
		org.assertj.core.api.Assertions.assertThat(countWorkspaceEvents()).isEqualTo(before);

		mockMvc.perform(get("/api/v1/identity/audit-events")
					.param("query", "audit-query-hidden")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void rejectsUnauthorizedRoleAndOversizedWindow() throws Exception {
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'SALES_MANAGER' where user_id = ?::uuid and workspace_id = ?::uuid",
				USER_ID, WORKSPACE_ID);
		mockMvc.perform(get("/api/v1/identity/audit-events").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isForbidden());

		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where user_id = ?::uuid and workspace_id = ?::uuid",
				USER_ID, WORKSPACE_ID);
		Instant to = Instant.now();
		mockMvc.perform(get("/api/v1/identity/audit-events")
					.param("occurredFrom", to.minus(91, ChronoUnit.DAYS).toString())
					.param("occurredTo", to.toString())
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
	}

	private void insert(String id, String workspaceId, String eventType, String objectType, String objectId,
			String requestId, Instant occurredAt) {
		jdbcTemplate.update("""
				insert into identity.audit_events
				(id, user_id, workspace_id, event_type, object_type, object_id, request_id, details, occurred_at)
				values (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, '{"source":"integration-test"}'::jsonb, ?::timestamptz)
				""", id, USER_ID, workspaceId, eventType, objectType, objectId, requestId, occurredAt.toString());
	}

	private Long countWorkspaceEvents() {
		return jdbcTemplate.queryForObject("select count(*) from identity.audit_events where workspace_id = ?::uuid",
				Long.class, WORKSPACE_ID);
	}
}
