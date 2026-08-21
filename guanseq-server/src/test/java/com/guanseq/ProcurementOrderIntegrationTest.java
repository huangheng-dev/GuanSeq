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
class ProcurementOrderIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String SUPPLIER = "81000000-0000-4000-8000-000000000001";
	private static final String BUY_MATERIAL = "42000000-0000-4000-8000-000000000003";
	private static final String MAKE_MATERIAL = "42000000-0000-4000-8000-000000000001";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	ProcurementOrderIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void exposesTenantScopedSuppliersOrdersAndPurchasableMaterials() throws Exception {
		mockMvc.perform(get("/api/v1/procurement/order-reference-data").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suppliers[?(@.code == 'SUP-0001')]").exists())
				.andExpect(jsonPath("$.suppliers[?(@.code == 'SUP-HIDDEN')]").doesNotExist())
				.andExpect(jsonPath("$.materials[?(@.code == 'BR-6204')]").exists())
				.andExpect(jsonPath("$.materials[?(@.code == 'GS-800')]").doesNotExist());
		mockMvc.perform(get("/api/v1/procurement/orders").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.orderNumber == 'PO-260815-001')]").exists())
				.andExpect(jsonPath("$.items[?(@.orderNumber == 'PO-HIDDEN')]").doesNotExist());
	}

	@Test
	void createsApprovesReleasesAndAuditsPurchaseOrder() throws Exception {
		LocalDate date = LocalDate.now().plusDays(10);
		MvcResult created = mockMvc.perform(post("/api/v1/procurement/orders")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "purchase-create-0001")
					.contentType(MediaType.APPLICATION_JSON).content(orderBody(BUY_MATERIAL, date)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.totalGrossAmount").value(9040)).andReturn();
		String id = extractId(created);
		performAction(id, "SUBMIT", 0, null, "PENDING_APPROVAL", 1);
		performAction(id, "APPROVE", 1, "价格与交期已复核", "APPROVED", 2);
		performAction(id, "RELEASE", 2, "下达供应商", "RELEASED", 3);
		Integer events = jdbcTemplate.queryForObject("select count(*) from procurement.purchase_order_events where order_id = cast(? as uuid)", Integer.class, id);
		assertThat(events).isEqualTo(4);
	}

	@Test
	void rejectsMakeMaterialOnPurchaseOrder() throws Exception {
		mockMvc.perform(post("/api/v1/procurement/orders").with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content(orderBody(MAKE_MATERIAL, LocalDate.now().plusDays(10))))
				.andExpect(status().isUnprocessableEntity());
	}

	private void performAction(String id, String action, long version, String comment, String target, long targetVersion) throws Exception {
		String commentJson = comment == null ? "null" : "\"" + comment + "\"";
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"%s\",\"expectedVersion\":%d,\"comment\":%s}".formatted(action, version, commentJson)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value(target)).andExpect(jsonPath("$.version").value(targetVersion));
	}

	private static String orderBody(String materialId, LocalDate date) {
		return """
				{"supplierId":"%s","currency":"CNY","taxRate":0.13,"requestedReceiptDate":"%s","promisedReceiptDate":"%s","buyer":"唐工","lines":[{"materialId":"%s","orderedQuantity":100,"unitPrice":80}]}
				""".formatted(SUPPLIER, date, date, materialId);
	}

	private static String extractId(MvcResult result) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 id"); return matcher.group(1);
	}
}
