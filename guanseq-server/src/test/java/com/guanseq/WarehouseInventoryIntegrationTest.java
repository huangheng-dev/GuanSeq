package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WarehouseInventoryIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String GS_BALANCE = "73000000-0000-4000-8000-000000000001";
	private static final String INSPECTION_BALANCE = "73000000-0000-4000-8000-000000000004";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	WarehouseInventoryIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void listsTenantInventoryAndReferenceData() throws Exception {
		mockMvc.perform(get("/api/v1/warehouse/inventory-balances?page=0&size=20")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.items[?(@.materialCode == 'GS-800' && @.availableQuantity == 6)]").exists())
				.andExpect(jsonPath("$.items[?(@.materialCode == 'MAT-HIDDEN')]").doesNotExist());

		mockMvc.perform(get("/api/v1/warehouse/inventory-reference-data").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.warehouses.length()").value(3))
				.andExpect(jsonPath("$.locations.length()").value(4));
	}

	@Test
	@Transactional
	void postsIdempotentMovementAndProtectsQuantityAndVersionBoundaries() throws Exception {
		String allocation = movement("ALLOCATE", 2, "销售订单预留", 0);
		mockMvc.perform(post("/api/v1/warehouse/inventory-balances/{id}/movements", GS_BALANCE)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "inventory-allocate-test-0001")
					.contentType(MediaType.APPLICATION_JSON).content(allocation))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.allocatedQuantity").value(8))
				.andExpect(jsonPath("$.availableQuantity").value(4))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(post("/api/v1/warehouse/inventory-balances/{id}/movements", GS_BALANCE)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "inventory-allocate-test-0001")
					.contentType(MediaType.APPLICATION_JSON).content(movement("ALLOCATE", 99, "重复请求", 0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.allocatedQuantity").value(8));

		mockMvc.perform(post("/api/v1/warehouse/inventory-balances/{id}/movements", GS_BALANCE)
					.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
					.content(movement("FREEZE", 1, "过期页面提交", 0)))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/warehouse/inventory-balances/{id}/movements", GS_BALANCE)
					.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
					.content(movement("ISSUE", 99, "越界出库", 1)))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	@Transactional
	void rejectsIssueFromInspectionStockAndUnauthorizedRole() throws Exception {
		mockMvc.perform(post("/api/v1/warehouse/inventory-balances/{id}/movements", INSPECTION_BALANCE)
					.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
					.content(movement("ISSUE", 1, "待检库存不可出库", 0)))
				.andExpect(status().isUnprocessableEntity());

		jdbcTemplate.update("""
				update identity.workspace_memberships set role_code = 'VIEWER'
				where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)
				""", "20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		mockMvc.perform(post("/api/v1/warehouse/inventory-balances/{id}/movements", GS_BALANCE)
					.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
					.content(movement("FREEZE", 1, "无权冻结", 0)))
				.andExpect(status().isForbidden());
	}

	private static String movement(String type, int quantity, String reason, long version) {
		return """
				{"movementType":"%s","quantity":%d,"reason":"%s","expectedVersion":%d}
				""".formatted(type, quantity, reason, version);
	}
}
