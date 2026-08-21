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
class ProductBomIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String GS_800 = "42000000-0000-4000-8000-000000000001";
	private static final String PM_45 = "42000000-0000-4000-8000-000000000002";
	private static final String BR_6204 = "42000000-0000-4000-8000-000000000003";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	ProductBomIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void createsUpdatesPublishesAndProtectsControlledBomVersion() throws Exception {
		mockMvc.perform(get("/api/v1/product/boms?page=0&size=20&status=ALL")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.bomNumber == 'BOM-260815-001')]").exists())
				.andExpect(jsonPath("$.items[?(@.bomNumber == 'BOM-HIDDEN')]").doesNotExist());

		MvcResult created = mockMvc.perform(post("/api/v1/product/boms")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "bom-create-test-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body("V9.TEST", 2, false)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.parentMaterialCode").value("PM-45"))
				.andExpect(jsonPath("$.lines[0].componentMaterialCode").value("BR-6204"))
				.andExpect(jsonPath("$.events[0].requestId").value("bom-create-test-0001"))
				.andReturn();
		String id = extractId(created);

		mockMvc.perform(put("/api/v1/product/boms/{id}", id)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody("V9.TEST", 3, 99)))
				.andExpect(status().isConflict());

		mockMvc.perform(put("/api/v1/product/boms/{id}", id)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody("V9.TEST", 3, 0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.lines[0].quantity").value(3));

		mockMvc.perform(post("/api/v1/product/boms/{id}/actions", id)
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "bom-publish-test-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"PUBLISH\",\"expectedVersion\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.events[0].action").value("PUBLISHED"));

		mockMvc.perform(put("/api/v1/product/boms/{id}", id)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody("V9.TEST", 4, 2)))
				.andExpect(status().isConflict());
	}

	@Test
	void rejectsSelfReferenceAndSecondPublishedVersion() throws Exception {
		mockMvc.perform(post("/api/v1/product/boms")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body("V9.SELF", 1, true)))
				.andExpect(status().isUnprocessableEntity());

		MvcResult second = mockMvc.perform(post("/api/v1/product/boms")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(bodyFor(GS_800, BR_6204, "V9.CONFLICT", 1)))
				.andExpect(status().isOk())
				.andReturn();
		String id = extractId(second);
		mockMvc.perform(post("/api/v1/product/boms/{id}/actions", id)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"PUBLISH\",\"expectedVersion\":0}"))
				.andExpect(status().isConflict());
	}

	@Test
	@Transactional
	void deniesMaintenanceForRoleOutsideProductBoundary() throws Exception {
		jdbcTemplate.update("""
				update identity.workspace_memberships set role_code = 'VIEWER'
				where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)
				""", "20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		try {
			mockMvc.perform(post("/api/v1/product/boms")
						.with(httpBasic(USERNAME, PASSWORD))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("V9.DENIED", 1, false)))
					.andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("""
					update identity.workspace_memberships set role_code = 'PLANNING_MANAGER'
					where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)
					""", "20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		}
	}

	private static String body(String versionCode, int quantity, boolean selfReference) {
		return bodyFor(PM_45, selfReference ? PM_45 : BR_6204, versionCode, quantity);
	}

	private static String bodyFor(String parentMaterialId, String componentMaterialId, String versionCode, int quantity) {
		return """
				{"parentMaterialId":"%s","usageType":"PRODUCTION","versionCode":"%s","baseQuantity":1,
				 "effectiveFrom":"%s","owner":"顾工","changeReason":"集成测试受控版本",
				 "lines":[{"componentMaterialId":"%s","quantity":%d,"scrapRate":0.01,"note":"测试组件"}]}
				""".formatted(parentMaterialId, versionCode, LocalDate.now(), componentMaterialId, quantity);
	}

	private static String updateBody(String versionCode, int quantity, long expectedVersion) {
		String create = body(versionCode, quantity, false).trim();
		return create.substring(0, create.length() - 1) + ",\"expectedVersion\":" + expectedVersion + "}";
	}

	private static String extractId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(body);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + body);
		return matcher.group(1);
	}
}
