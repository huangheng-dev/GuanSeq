package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

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
@SpringBootTest(properties = {
		"spring.flyway.locations=classpath:db/migration",
		"guanseq.bootstrap.enabled=true",
		"guanseq.bootstrap.token=pilot-bootstrap-token-with-32-characters"
})
class InitialWorkspaceBootstrapIntegrationTest {

	private static final String TOKEN = "pilot-bootstrap-token-with-32-characters";
	private static final String BODY = """
			{
			  "tenantCode":"PILOT-COMPANY",
			  "tenantName":"试点制造有限公司",
			  "plantCode":"PILOT-PLANT",
			  "plantName":"试点一厂",
			  "workspaceCode":"PILOT-WORKSPACE",
			  "workspaceName":"试点一厂工作区",
			  "externalUsername":"lin.hao",
			  "displayName":"林浩"
			}
			""";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	InitialWorkspaceBootstrapIntegrationTest(
			@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void initializesExactlyOnceUnderConcurrentRequestsAndCreatesAuditableAdminSession() throws Exception {
		mockMvc.perform(post("/api/v1/bootstrap/initial-workspace")
					.contentType(MediaType.APPLICATION_JSON)
					.content(BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

		mockMvc.perform(post("/api/v1/bootstrap/initial-workspace")
					.header("X-GuanSeq-Bootstrap-Token", TOKEN)
					.contentType(MediaType.APPLICATION_JSON)
					.content(BODY.replace("PILOT-COMPANY", "invalid code")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> performBootstrap("pilot-bootstrap-concurrent-1", ready, start));
			var second = executor.submit(() -> performBootstrap("pilot-bootstrap-concurrent-2", ready, start));
			ready.await();
			start.countDown();
			List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus())
					.stream()
					.sorted()
					.toList();
			assertThat(statuses).containsExactly(201, 409);
		}

		assertThat(count("identity.organization_units")).isEqualTo(2);
		assertThat(count("identity.workspaces")).isEqualTo(1);
		assertThat(count("identity.user_accounts")).isEqualTo(1);
		assertThat(count("identity.workspace_memberships")).isEqualTo(1);
		assertThat(count("identity.user_workspace_preferences")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from identity.audit_events where event_type = 'SYSTEM_BOOTSTRAPPED'",
				Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select status from identity.system_bootstrap where singleton_key = true",
				String.class)).isEqualTo("COMPLETED");

		mockMvc.perform(post("/api/v1/bootstrap/initial-workspace")
					.header("X-GuanSeq-Bootstrap-Token", TOKEN)
					.contentType(MediaType.APPLICATION_JSON)
					.content(BODY))
				.andExpect(status().isConflict());

		mockMvc.perform(get("/api/v1/me/workspaces").with(httpBasic("lin.hao", "guanseq_dev")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("lin.hao"))
				.andExpect(jsonPath("$.workspaces[0].roleCode").value("ADMIN"))
				.andExpect(jsonPath("$.workspaces[0].current").value(true));
	}

	private MvcResult performBootstrap(String requestId, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await();
		return mockMvc.perform(post("/api/v1/bootstrap/initial-workspace")
					.header("X-GuanSeq-Bootstrap-Token", TOKEN)
					.header("X-Request-Id", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(BODY))
				.andReturn();
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
	}
}
