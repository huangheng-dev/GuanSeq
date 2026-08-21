package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class SalesOrderIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String CUSTOMER = "41000000-0000-4000-8000-000000000001";
	private static final String MATERIAL = "42000000-0000-4000-8000-000000000001";
	private static final String INACTIVE_CUSTOMER = "41000000-0000-4000-8000-000000000004";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	SalesOrderIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void exposesOnlyActiveTenantScopedReferenceDataAndOrders() throws Exception {
		mockMvc.perform(get("/api/v1/sales/order-reference-data").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customers[?(@.code == 'CUS-0001')]").exists())
				.andExpect(jsonPath("$.materials[?(@.code == 'GS-800')]").exists())
				.andExpect(jsonPath("$.customers[?(@.code == 'CUS-0004')]").doesNotExist())
				.andExpect(jsonPath("$.customers[?(@.code == 'CUS-HIDDEN')]").doesNotExist())
				.andExpect(jsonPath("$.materials[?(@.code == 'MAT-HIDDEN')]").doesNotExist());

		mockMvc.perform(get("/api/v1/sales/orders")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "sales-list-0001"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "sales-list-0001"))
				.andExpect(jsonPath("$.items[?(@.orderNumber == 'SO-260815-001')]").exists())
				.andExpect(jsonPath("$.items[?(@.orderNumber == 'SO-HIDDEN')]").doesNotExist());
	}

	@Test
	void createsUpdatesSubmitsApprovesReleasesAndAuditsOrder() throws Exception {
		LocalDate requested = LocalDate.now().plusDays(12);
		LocalDate promised = LocalDate.now().plusDays(11);
		MvcResult created = mockMvc.perform(post("/api/v1/sales/orders")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "sales-create-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content(orderBody(CUSTOMER, MATERIAL, requested, promised, "12", "28000")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.totalNetAmount").value(336000))
				.andExpect(jsonPath("$.totalGrossAmount").value(379680))
				.andReturn();
		String orderId = extractId(created);

		mockMvc.perform(put("/api/v1/sales/orders/{id}", orderId)
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "sales-update-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody(CUSTOMER, MATERIAL, requested, promised, "10", "30000", 0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.totalGrossAmount").value(339000));

		mockMvc.perform(put("/api/v1/sales/orders/{id}", orderId)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateBody(CUSTOMER, MATERIAL, requested, promised, "10", "30000", 0)))
				.andExpect(status().isConflict());

		performAction(orderId, "SUBMIT", 1, null, "sales-submit-0001", "PENDING_APPROVAL", 2);
		performAction(orderId, "APPROVE", 2, "金额与交期已复核", "sales-approve-0001", "APPROVED", 3);
		performAction(orderId, "RELEASE", 3, "下达至计划", "sales-release-0001", "RELEASED", 4);

		Integer auditCount = jdbcTemplate.queryForObject(
				"select count(*) from sales.change_events where order_id = cast(? as uuid) and request_id is not null",
				Integer.class,
				orderId);
		assertThat(auditCount).isEqualTo(5);

		Integer demandCount = jdbcTemplate.queryForObject(
				"select count(*) from planning.independent_demands where source_type = 'SALES_ORDER' and source_id = cast(? as uuid) and status = 'ACTIVE'",
				Integer.class,
				orderId);
		assertThat(demandCount).isEqualTo(1);
	}

	@Test
	void rejectsInactiveCustomerAndRequiresRejectionReason() throws Exception {
		LocalDate date = LocalDate.now().plusDays(8);
		mockMvc.perform(post("/api/v1/sales/orders")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(orderBody(INACTIVE_CUSTOMER, MATERIAL, date, date, "1", "100")))
				.andExpect(status().isUnprocessableEntity());

		mockMvc.perform(post("/api/v1/sales/orders/51000000-0000-4000-8000-000000000002/actions")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"REJECT\",\"expectedVersion\":0}"))
				.andExpect(status().isBadRequest());
	}

	private void performAction(String id, String action, long expectedVersion, String comment, String requestId, String statusValue, long version) throws Exception {
		String commentJson = comment == null ? "null" : "\"" + comment + "\"";
		mockMvc.perform(post("/api/v1/sales/orders/{id}/actions", id)
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"%s\",\"expectedVersion\":%d,\"comment\":%s}".formatted(action, expectedVersion, commentJson)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(statusValue))
				.andExpect(jsonPath("$.version").value(version));
	}

	private static String orderBody(String customerId, String materialId, LocalDate requested, LocalDate promised, String quantity, String unitPrice) {
		return """
				{"customerId":"%s","currency":"CNY","taxRate":0.13,"requestedDeliveryDate":"%s","promisedDeliveryDate":"%s","owner":"沈妍","lines":[{"materialId":"%s","quantity":%s,"unitPrice":%s}]}
				""".formatted(customerId, requested, promised, materialId, quantity, unitPrice);
	}

	private static String updateBody(String customerId, String materialId, LocalDate requested, LocalDate promised, String quantity, String unitPrice, long version) {
		return orderBody(customerId, materialId, requested, promised, quantity, unitPrice).trim().replaceFirst("}$", ",\"expectedVersion\":" + version + "}");
	}

	private static String extractId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(body);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + body);
		return matcher.group(1);
	}
}
