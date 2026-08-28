package com.guanseq;

import java.util.List;

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

import com.guanseq.platform.infrastructure.web.RequestIdFilter;
import com.jayway.jsonpath.JsonPath;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrganizationStructureIntegrationTest {
	private static final String ADMIN_USERNAME = "lin.hao";
	private static final String ADMIN_PASSWORD = "guanseq_dev";
	private static final String ADMIN_USER_ID = "20000000-0000-4000-8000-000000000001";
	private static final String WORKSPACE_ID = "10000000-0000-4000-8000-000000000101";
	private static final String PLANT_ID = "00000000-0000-4000-8000-000000000101";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	OrganizationStructureIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@BeforeEach
	void selectKnownWorkspace() {
		jdbcTemplate.update("update identity.user_workspace_preferences set current_workspace_id = ?::uuid where user_id = ?::uuid",
				WORKSPACE_ID, ADMIN_USER_ID);
		jdbcTemplate.update("""
				insert into identity.user_accounts (id, tenant_organization_id, username, display_name)
				values ('20000000-0000-4000-8000-000000000091', '00000000-0000-4000-8000-000000000001', 'org.viewer', '组织查看员')
				on conflict (id) do nothing
				""");
		jdbcTemplate.update("""
				insert into identity.workspace_memberships (id, user_id, workspace_id, organization_unit_id, role_code)
				values ('30000000-0000-4000-8000-000000000191', '20000000-0000-4000-8000-000000000091',
				'10000000-0000-4000-8000-000000000101', '00000000-0000-4000-8000-000000000101', 'PRODUCTION_OPERATOR')
				on conflict (id) do nothing
				""");
		jdbcTemplate.update("""
				insert into identity.user_workspace_preferences (user_id, current_workspace_id)
				values ('20000000-0000-4000-8000-000000000091', '10000000-0000-4000-8000-000000000101')
				on conflict (user_id) do nothing
				""");
	}

	@Test
	void governsCurrentWorkspaceSitesOwnersMemberAssignmentsConcurrencyAndAudit() throws Exception {
		mockMvc.perform(get("/api/v1/identity/organization-structure")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/identity/organization-structure").with(user("org.viewer")))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/identity/organization-structure")
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).header("X-Request-Id", "org-list-0001"))
				.andExpect(status().isOk()).andExpect(header().string("X-Request-Id", "org-list-0001"))
				.andExpect(jsonPath("$.company.unitType").value("COMPANY"))
				.andExpect(jsonPath("$.operatingUnit.id").value(PLANT_ID))
				.andExpect(jsonPath("$.workspace.id").value(WORKSPACE_ID))
				.andExpect(jsonPath("$.members[?(@.username == 'lin.hao')].organizationUnitId").value(PLANT_ID));

		MvcResult created = mockMvc.perform(post("/api/v1/identity/organization-structure/units")
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).header("X-Request-Id", "org-site-create-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"code":"SITE-PILOT-01","name":"试点总装现场","responsibleUserId":"20000000-0000-4000-8000-000000000001"}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.siteUnits[?(@.code == 'SITE-PILOT-01')].status").value("ACTIVE"))
				.andReturn();
		List<String> siteIds = JsonPath.parse(created.getResponse().getContentAsString()).read("$.siteUnits[?(@.code == 'SITE-PILOT-01')].id");
		String siteId = siteIds.getFirst();

		mockMvc.perform(put("/api/v1/identity/organization-structure/members/{userId}", ADMIN_USER_ID)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).header("X-Request-Id", "org-member-assign-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"organizationUnitId":"%s","expectedMembershipVersion":0,"reason":"试点总装现场责任归属"}
							""".formatted(siteId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members[?(@.username == 'lin.hao')].organizationUnitId").value(siteId))
				.andExpect(jsonPath("$.members[?(@.username == 'lin.hao')].membershipVersion").value(1));

		mockMvc.perform(post("/api/v1/identity/organization-structure/units/{unitId}/actions", siteId)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"DEACTIVATE\",\"expectedVersion\":0,\"reason\":\"试点现场阶段结束\"}"))
				.andExpect(status().isConflict());

		mockMvc.perform(put("/api/v1/identity/organization-structure/members/{userId}", ADMIN_USER_ID)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"organizationUnitId\":\"%s\",\"expectedMembershipVersion\":1,\"reason\":\"回归工厂默认组织\"}".formatted(PLANT_ID)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/identity/organization-structure/units/{unitId}/actions", siteId)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).header("X-Request-Id", "org-site-stop-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"DEACTIVATE\",\"expectedVersion\":0,\"reason\":\"试点现场阶段结束\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.siteUnits[?(@.id == '%s')].status".formatted(siteId)).value("INACTIVE"));

		mockMvc.perform(post("/api/v1/identity/organization-structure/units/{unitId}/actions", siteId)
					.with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"ACTIVATE\",\"expectedVersion\":0,\"reason\":\"使用过期版本恢复\"}"))
				.andExpect(status().isConflict());

		Integer auditCount = jdbcTemplate.queryForObject("""
				select count(*) from identity.audit_events where request_id in
				('org-site-create-0001', 'org-member-assign-0001', 'org-site-stop-0001')
				""", Integer.class);
		assertThat(auditCount).isEqualTo(3);
	}
}
