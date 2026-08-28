package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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

import com.jayway.jsonpath.JsonPath;
import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WorkspaceUserManagementIntegrationTest {

	private static final String ADMIN_USERNAME = "lin.hao";
	private static final String ADMIN_PASSWORD = "guanseq_dev";
	private static final String ADMIN_USER_ID = "20000000-0000-4000-8000-000000000001";
	private static final String EAST_WORKSPACE_ID = "10000000-0000-4000-8000-000000000101";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	WorkspaceUserManagementIntegrationTest(
			@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@BeforeEach
	void selectKnownWorkspace() {
		jdbcTemplate.update(
				"update identity.user_workspace_preferences set current_workspace_id = ?::uuid where user_id = ?::uuid",
				EAST_WORKSPACE_ID,
				ADMIN_USER_ID);
	}

	@Test
	void governsCurrentWorkspaceUsersWithTenantPermissionConcurrencyAndAudit() throws Exception {
		mockMvc.perform(get("/api/v1/identity/workspace-users"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/identity/workspace-users")
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.header("X-Request-Id", "workspace-users-list-0001"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "workspace-users-list-0001"))
				.andExpect(jsonPath("$.workspaceId").value(EAST_WORKSPACE_ID))
				.andExpect(jsonPath("$.availableRoles[?(@.code == 'ADMIN')].name").value("系统管理员"))
				.andExpect(jsonPath("$.items[?(@.username == 'lin.hao')].roleCode").value("ADMIN"));

		mockMvc.perform(get("/api/v1/identity/role-permissions"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/identity/role-permissions")
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.header("X-Request-Id", "role-permissions-list-0001"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "role-permissions-list-0001"))
				.andExpect(jsonPath("$.workspaceId").value(EAST_WORKSPACE_ID))
				.andExpect(jsonPath("$.catalogVersion").value("2026-08-28.2"))
				.andExpect(jsonPath("$.roles.length()").value(13))
				.andExpect(jsonPath("$.groups[?(@.moduleCode == 'IDENTITY')].moduleName").value("身份与工作区"))
				.andExpect(jsonPath("$.groups[*].permissions[*].code", org.hamcrest.Matchers.hasItem("FINANCE_ACCOUNTING_PERIOD_REOPEN")))
				.andExpect(jsonPath("$.groups[*].permissions[?(@.code == 'FINANCE_ACCOUNTING_PERIOD_REOPEN')].roleCodes[0]").value("ADMIN"));

		mockMvc.perform(post("/api/v1/identity/workspace-users")
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"pilot.invalid","displayName":"无效角色","roleCode":"SUPER_ADMIN"}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		MvcResult created = mockMvc.perform(post("/api/v1/identity/workspace-users")
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.header("X-Request-Id", "workspace-user-create-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"pilot.operator","displayName":"试点操作员","roleCode":"PRODUCTION_OPERATOR"}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.roleCode").value("PRODUCTION_OPERATOR"))
				.andExpect(jsonPath("$.userVersion").value(0))
				.andExpect(jsonPath("$.membershipVersion").value(0))
				.andReturn();
		String userId = JsonPath.parse(created.getResponse().getContentAsString()).read("$.userId");

		mockMvc.perform(post("/api/v1/identity/workspace-users")
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"pilot.operator","displayName":"重复用户","roleCode":"PRODUCTION_OPERATOR"}
							"""))
				.andExpect(status().isConflict());

		mockMvc.perform(get("/api/v1/identity/workspace-users").with(user("pilot.operator")))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/identity/role-permissions").with(user("pilot.operator")))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/identity/workspace-users/{userId}", userId)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"displayName":"缺少并发版本","roleCode":"PRODUCTION_MANAGER"}
							"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/identity/workspace-users/{userId}", userId)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.header("X-Request-Id", "workspace-user-update-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"displayName":"试点生产主管","roleCode":"PRODUCTION_MANAGER","expectedUserVersion":0,"expectedMembershipVersion":0}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("试点生产主管"))
				.andExpect(jsonPath("$.roleCode").value("PRODUCTION_MANAGER"))
				.andExpect(jsonPath("$.userVersion").value(1))
				.andExpect(jsonPath("$.membershipVersion").value(1));

		mockMvc.perform(put("/api/v1/identity/workspace-users/{userId}", userId)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"displayName":"过期更新","roleCode":"PRODUCTION_MANAGER","expectedUserVersion":0,"expectedMembershipVersion":0}
							"""))
				.andExpect(status().isConflict());

		mockMvc.perform(put("/api/v1/identity/workspace-users/{userId}", ADMIN_USER_ID)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"displayName":"不能自改","roleCode":"ADMIN","expectedUserVersion":0,"expectedMembershipVersion":0}
							"""))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/identity/workspace-users/{userId}/actions", userId)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.header("X-Request-Id", "workspace-user-deactivate-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"action":"DEACTIVATE","expectedMembershipVersion":1,"reason":"试点岗位暂时停止访问"}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.membershipStatus").value("INACTIVE"))
				.andExpect(jsonPath("$.membershipVersion").value(2));

		mockMvc.perform(get("/api/v1/me/workspaces").with(user("pilot.operator")))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/identity/workspace-users/{userId}/actions", userId)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
					.header("X-Request-Id", "workspace-user-activate-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"action":"ACTIVATE","expectedMembershipVersion":2,"reason":"恢复试点生产岗位访问"}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.membershipVersion").value(3));

		mockMvc.perform(get("/api/v1/identity/workspace-users?query=pilot.operator&status=ACTIVE&page=0&size=10")
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].userId").value(userId));

		Integer auditCount = jdbcTemplate.queryForObject(
				"""
				select count(*) from identity.audit_events
				where object_id = ?
				and event_type in ('USER_PROVISIONED', 'WORKSPACE_MEMBER_UPDATED',
					'WORKSPACE_MEMBER_DEACTIVATED', 'WORKSPACE_MEMBER_ACTIVATED')
				""",
				Integer.class,
				userId);
		assertThat(auditCount).isEqualTo(4);
		assertThat(jdbcTemplate.queryForObject(
				"select details ->> 'reason' from identity.audit_events where request_id = 'workspace-user-deactivate-0001'",
				String.class)).isEqualTo("试点岗位暂时停止访问");
	}
}
