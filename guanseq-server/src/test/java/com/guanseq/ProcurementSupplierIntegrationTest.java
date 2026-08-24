package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProcurementSupplierIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String TENANT = "00000000-0000-4000-8000-000000000001";
	private static final String EAST_PLANT = "00000000-0000-4000-8000-000000000101";
	private static final String EAST_WS = "10000000-0000-4000-8000-000000000101";
	private static final String USER_ID = "20000000-0000-4000-8000-000000000001";
	private static final String HIDDEN_SUPPLIER = "81000000-0000-4000-8000-000000000099";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbc;
	private final EntityManager entityManager;

	ProcurementSupplierIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbc,
			@Autowired EntityManager entityManager) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbc = jdbc;
		this.entityManager = entityManager;
	}

	@Test
	@Transactional
	void listsCreatesUpdatesDeactivatesAndAuditsSuppliers() throws Exception {
		mockMvc.perform(get("/api/v1/procurement/suppliers").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.items[?(@.code == 'SUP-HIDDEN')]").doesNotExist());

		MvcResult created = mockMvc.perform(post("/api/v1/procurement/suppliers")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "sup-create-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"code":"SUP-T01","name":"测试供应商有限公司","contactName":"李工","contactPhone":"139-0000-1234"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUP-T01"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andReturn();
		String supplierId = extractId(created);

		mockMvc.perform(put("/api/v1/procurement/suppliers/{id}", supplierId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "sup-update-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"测试供应商（更新）","contactName":"李工程师","contactPhone":"139-0000-5678","expectedVersion":0}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("测试供应商（更新）"));

		mockMvc.perform(post("/api/v1/procurement/suppliers/{id}/actions", supplierId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "sup-disable-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status":"INACTIVE","expectedVersion":1}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INACTIVE"));

		mockMvc.perform(post("/api/v1/procurement/suppliers/{id}/actions", supplierId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "sup-enable-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status":"ACTIVE","expectedVersion":2}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		entityManager.flush();
		var rows = jdbc.queryForList(
				"select action, request_id from procurement.supplier_events where supplier_id = cast(? as uuid) order by occurred_at",
				supplierId);
		org.assertj.core.api.Assertions.assertThat(rows).as("events=" + rows).hasSize(4);
	}

	@Test
	@Transactional
	void rejectsDuplicateCode() throws Exception {
		mockMvc.perform(post("/api/v1/procurement/suppliers")
				.with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"code":"SUP-0001","name":"重复编码"}
						"""))
				.andExpect(status().isConflict());
	}

	@Test
	@Transactional
	void rejectsWritesForNonManagerRole() throws Exception {
		String wsId = UUID.randomUUID().toString();
		String memberId = UUID.randomUUID().toString();
		jdbc.update("""
				INSERT INTO identity.workspaces (id, code, name, tenant_organization_id, operating_organization_id, status)
				VALUES (cast(? as uuid), 'BUYER-WS', '采购专员工作台', cast(? as uuid), cast(? as uuid), 'ACTIVE')
				""", wsId, TENANT, EAST_PLANT);
		jdbc.update("""
				INSERT INTO identity.workspace_memberships (id, user_id, workspace_id, role_code, status)
				VALUES (cast(? as uuid), cast(? as uuid), cast(? as uuid), 'PROCUREMENT_BUYER', 'ACTIVE')
				""", memberId, USER_ID, wsId);
		jdbc.update("""
				UPDATE identity.user_workspace_preferences SET current_workspace_id = cast(? as uuid) WHERE user_id = cast(? as uuid)
				""", wsId, USER_ID);
		try {
			mockMvc.perform(get("/api/v1/procurement/suppliers").with(httpBasic(USERNAME, PASSWORD)))
					.andExpect(status().isOk());
			mockMvc.perform(post("/api/v1/procurement/suppliers")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"code":"SUP-FORBIDDEN","name":"无权创建"}
							"""))
					.andExpect(status().isForbidden());
		} finally {
			jdbc.update("""
					UPDATE identity.user_workspace_preferences SET current_workspace_id = cast(? as uuid) WHERE user_id = cast(? as uuid)
					""", EAST_WS, USER_ID);
		}
	}

	@Test
	@Transactional
	void isolatesCrossTenantSupplierAccess() throws Exception {
		mockMvc.perform(put("/api/v1/procurement/suppliers/{id}", HIDDEN_SUPPLIER)
				.with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"跨租户篡改","contactName":null,"contactPhone":null,"expectedVersion":0}
						"""))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/procurement/suppliers/{id}/actions", HIDDEN_SUPPLIER)
				.with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status":"INACTIVE","expectedVersion":0}
						"""))
				.andExpect(status().isNotFound());
	}

	@Test
	@Transactional
	void rejectsStaleVersionOnUpdate() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/procurement/suppliers")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "sup-version-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"code":"SUP-VER","name":"版本冲突测试"}
						"""))
				.andExpect(status().isOk()).andReturn();
		String id = extractId(created);

		// First update with version 0 succeeds
		mockMvc.perform(put("/api/v1/procurement/suppliers/{id}", id)
				.with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"版本冲突测试-改1","contactName":null,"contactPhone":null,"expectedVersion":0}
						"""))
				.andExpect(status().isOk());

		// Stale version 0 should conflict
		mockMvc.perform(put("/api/v1/procurement/suppliers/{id}", id)
				.with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"版本冲突测试-改2","contactName":null,"contactPhone":null,"expectedVersion":0}
						"""))
				.andExpect(status().isConflict());
	}

	@Test
	@Transactional
	void preventsInactiveSupplierFromNewPurchaseOrder() throws Exception {
		// Deactivate SUP-0002
		mockMvc.perform(post("/api/v1/procurement/suppliers/{id}/actions", "81000000-0000-4000-8000-000000000002")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "sup-deactivate-po-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status":"INACTIVE","expectedVersion":0}
						"""))
				.andExpect(status().isOk());

		// Creating PO with inactive supplier should fail
		LocalDate date = LocalDate.now().plusDays(10);
		mockMvc.perform(post("/api/v1/procurement/orders")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "po-inactive-sup-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"supplierId":"81000000-0000-4000-8000-000000000002","currency":"CNY","taxRate":0.13,"requestedReceiptDate":"%s","promisedReceiptDate":"%s","buyer":"唐工","lines":[{"materialId":"42000000-0000-4000-8000-000000000003","orderedQuantity":10,"unitPrice":80}]}
						""".formatted(date, date)))
				.andExpect(status().isUnprocessableEntity());
	}

	private static String extractId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		var m = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(body);
		if (!m.find()) throw new AssertionError("missing id: " + body);
		return m.group(1);
	}
}
