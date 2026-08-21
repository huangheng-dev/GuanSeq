package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductionOrderIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String MAKE_MATERIAL = "42000000-0000-4000-8000-000000000001";
	private static final String BUY_MATERIAL = "42000000-0000-4000-8000-000000000003";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	ProductionOrderIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build(); this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void exposesOnlyTenantScopedProductionOrdersAndMakeMaterials() throws Exception {
		mockMvc.perform(get("/api/v1/production/order-reference-data").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.materials[?(@.code == 'GS-800')]").exists())
				.andExpect(jsonPath("$.materials[?(@.code == 'BR-6204')]").doesNotExist());
		mockMvc.perform(get("/api/v1/production/orders").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.orderNumber == 'MO-260815-012')]").exists())
				.andExpect(jsonPath("$.items[?(@.orderNumber == 'MO-HIDDEN')]").doesNotExist());
	}

	@Test
	void createsReleasesStartsAndAuditsProductionOrder() throws Exception {
		LocalDate start = LocalDate.now().plusDays(1); LocalDate receipt = start.plusDays(5);
		MvcResult created = mockMvc.perform(post("/api/v1/production/orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "production-create-0001").contentType(MediaType.APPLICATION_JSON)
				.content(orderBody(MAKE_MATERIAL, start, receipt))).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.plannedQuantity").value(12)).andReturn();
		String id = extractId(created); performAction(id, "RELEASE", 0, null, "RELEASED", 1);
		performAction(id, "START", 1, "物料齐套后开工", "IN_PROGRESS", 2);
		mockMvc.perform(get("/api/v1/production/orders/{id}", id).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.completedQuantity").value(0))
				.andExpect(jsonPath("$.reportableQuantity").value(12));
		Integer events = jdbcTemplate.queryForObject("select count(*) from production.production_order_events where order_id = cast(? as uuid)", Integer.class, id);
		assertThat(events).isEqualTo(3);
		mockMvc.perform(post("/api/v1/production/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"COMPLETE\",\"expectedVersion\":2}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsPurchasedMaterialOnProductionOrder() throws Exception {
		LocalDate start = LocalDate.now().plusDays(1);
		mockMvc.perform(post("/api/v1/production/orders").with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content(orderBody(BUY_MATERIAL, start, start.plusDays(5))))
				.andExpect(status().isUnprocessableEntity());
	}

	private void performAction(String id, String action, long version, String comment, String target, long targetVersion) throws Exception {
		String commentJson = comment == null ? "null" : "\"" + comment + "\"";
		mockMvc.perform(post("/api/v1/production/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"%s\",\"expectedVersion\":%d,\"comment\":%s}".formatted(action, version, commentJson)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value(target)).andExpect(jsonPath("$.version").value(targetVersion));
	}

	private static String orderBody(String materialId, LocalDate start, LocalDate receipt) {
		return """
				{"materialId":"%s","plannedQuantity":12,"plannedStartDate":"%s","plannedReceiptDate":"%s","workshop":"总装一车间","owner":"周启明","sourceType":"MANUAL","sourceId":null,"sourceNumber":null}
				""".formatted(materialId, start, receipt);
	}

	private static String extractId(MvcResult result) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 id"); return matcher.group(1);
	}
}
