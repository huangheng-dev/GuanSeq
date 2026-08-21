package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FinanceOrderProfitIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String CUSTOMER = "41000000-0000-4000-8000-000000000001";
	private static final String MATERIAL = "42000000-0000-4000-8000-000000000001";
	private static final String FINISHED_GOODS_WAREHOUSE = "71000000-0000-4000-8000-000000000003";
	private static final String SEEDED_ORDER = "51000000-0000-4000-8000-000000000004";
	private static final String SEEDED_ORDER_LINE = "52000000-0000-4000-8000-000000000004";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	FinanceOrderProfitIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void settlesShippedOrderWithMaterialLaborAndOverheadCostsAndIsIdempotent() throws Exception {
		LocalDate shipDate = LocalDate.now().plusDays(1);
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "finance-shipment-0001").contentType(MediaType.APPLICATION_JSON)
				.content(shipmentBody(SEEDED_ORDER, SEEDED_ORDER_LINE, FINISHED_GOODS_WAREHOUSE, shipDate, 2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalShippedQuantity").value(2));

		MvcResult settled = mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", SEEDED_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "finance-profit-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.salesOrderId").value(SEEDED_ORDER))
				.andExpect(jsonPath("$.shippedQuantity").value(2))
				.andExpect(jsonPath("$.revenue").value(60000.00))
				.andExpect(jsonPath("$.materialCost").value(4240.00))
				.andExpect(jsonPath("$.laborCost").value(385.00))
				.andExpect(jsonPath("$.overheadCost").value(215.00))
				.andExpect(jsonPath("$.processingCost").value(600.00))
				.andExpect(jsonPath("$.totalCost").value(4840.00))
				.andExpect(jsonPath("$.grossProfit").value(55160.00))
				.andExpect(jsonPath("$.grossMargin").value(0.919333))
				.andExpect(jsonPath("$.costStatus").value("COMPLETE"))
				.andExpect(jsonPath("$.lines[0].productionOrderNumber").value("MO-260815-012"))
				.andExpect(jsonPath("$.lines[0].acceptedQuantity").value(2))
				.andExpect(jsonPath("$.missingItems.length()").value(0))
				.andReturn();
		String settlementId = field(settled, "id");

		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", SEEDED_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "finance-profit-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(settlementId));

		mockMvc.perform(get("/api/v1/finance/order-profits?query=SO-260815-004").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(settlementId))
				.andExpect(jsonPath("$.items[0].orderStatus").value("PARTIALLY_SHIPPED"));
	}

	@Test
	@Transactional
	void rejectsSettlementWithoutShipmentAndWithoutPermission() throws Exception {
		String orderId = createReleasedOrder(1);
		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", orderId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "finance-profit-unshipped-0001"))
				.andExpect(status().isUnprocessableEntity());

		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'PRODUCTION_OPERATOR' where id = '30000000-0000-4000-8000-000000000101'");
		try {
			mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", orderId)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "finance-profit-denied-0001"))
					.andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where id = '30000000-0000-4000-8000-000000000101'");
		}
	}

	@Test
	@Transactional
	void marksSettlementMissingCostWhenProductionEvidenceIsAbsent() throws Exception {
		String orderId = createReleasedOrder(1);
		String orderLineId = jdbcTemplate.queryForObject(
				"select id from sales.order_lines where order_id = cast(? as uuid)", String.class, orderId);
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "finance-shipment-missing-cost-0001").contentType(MediaType.APPLICATION_JSON)
				.content(shipmentBody(orderId, orderLineId, FINISHED_GOODS_WAREHOUSE, LocalDate.now().plusDays(1), 1)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", orderId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "finance-profit-missing-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.revenue").value(28000.00))
				.andExpect(jsonPath("$.materialCost").value(0.00))
				.andExpect(jsonPath("$.costStatus").value("MISSING_COST"))
				.andExpect(jsonPath("$.missingItems[0]").value(org.hamcrest.Matchers.containsString("缺少关联生产订单")));
	}

	@Test
	@Transactional
	void keepsMaterialCostButMarksMissingWhenWorkCenterRatesAreUnavailable() throws Exception {
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "finance-shipment-missing-rate-0001").contentType(MediaType.APPLICATION_JSON)
				.content(shipmentBody(SEEDED_ORDER, SEEDED_ORDER_LINE, FINISHED_GOODS_WAREHOUSE, LocalDate.now().plusDays(1), 2)))
				.andExpect(status().isOk());
		jdbcTemplate.update("update finance.work_center_cost_rates set status = 'INACTIVE' where work_center_code in ('WC-ASM-01','WC-ELE-01','WC-TEST-01')");

		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", SEEDED_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "finance-profit-missing-rate-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.materialCost").value(4240.00))
				.andExpect(jsonPath("$.laborCost").value(0.00))
				.andExpect(jsonPath("$.overheadCost").value(0.00))
				.andExpect(jsonPath("$.costStatus").value("MISSING_COST"))
				.andExpect(jsonPath("$.missingItems[0]").value(org.hamcrest.Matchers.containsString("工作中心成本费率")));
	}

	@Test
	@Transactional
	void keepsOverheadButMarksMissingWhenApprovedActualLaborIsUnavailable() throws Exception {
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "finance-shipment-missing-labor-0001").contentType(MediaType.APPLICATION_JSON)
				.content(shipmentBody(SEEDED_ORDER, SEEDED_ORDER_LINE, FINISHED_GOODS_WAREHOUSE, LocalDate.now().plusDays(1), 2)))
				.andExpect(status().isOk());
		jdbcTemplate.update("delete from production.operation_labor_events where entry_id in (select id from production.operation_labor_entries where order_id = cast(? as uuid))", "91000000-0000-4000-8000-000000000001");
		jdbcTemplate.update("delete from production.operation_labor_entries where order_id = cast(? as uuid)", "91000000-0000-4000-8000-000000000001");

		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", SEEDED_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "finance-profit-missing-labor-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.materialCost").value(4240.00))
				.andExpect(jsonPath("$.laborCost").value(0.00))
				.andExpect(jsonPath("$.overheadCost").value(215.00))
				.andExpect(jsonPath("$.costStatus").value("MISSING_COST"))
				.andExpect(jsonPath("$.missingItems[0]").value(org.hamcrest.Matchers.containsString("已审核实际人工工时")));
	}

	private String createReleasedOrder(int quantity) throws Exception {
		LocalDate date = LocalDate.now().plusDays(5);
		String body = """
				{"customerId":"%s","currency":"CNY","taxRate":0.13,"requestedDeliveryDate":"%s","promisedDeliveryDate":"%s","owner":"沈妍","lines":[{"materialId":"%s","quantity":%d,"unitPrice":28000}]}
				""".formatted(CUSTOMER, date, date, MATERIAL, quantity);
		MvcResult created = mockMvc.perform(post("/api/v1/sales/orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "finance-test-order-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andReturn();
		String id = field(created, "id");
		performAction(id, "SUBMIT", 0);
		performAction(id, "APPROVE", 1);
		performAction(id, "RELEASE", 2);
		return id;
	}

	private void performAction(String id, String action, long version) throws Exception {
		mockMvc.perform(post("/api/v1/sales/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"action":"%s","expectedVersion":%d,"comment":"利润测试"}
						""".formatted(action, version)))
				.andExpect(status().isOk());
	}

	private static String shipmentBody(String orderId, String lineId, String warehouseId, LocalDate plannedDate, Number quantity) {
		return """
				{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"利润自动化测试","lines":[{"orderLineId":"%s","shippedQuantity":%d}]}
				""".formatted(orderId, warehouseId, plannedDate, lineId, quantity);
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(name) + "\\\":\\\"([^\\\"]+)\\\"").matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}
}
