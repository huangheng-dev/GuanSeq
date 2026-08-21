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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SalesShipmentIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String CUSTOMER = "41000000-0000-4000-8000-000000000001";
	private static final String MATERIAL = "42000000-0000-4000-8000-000000000001";
	private static final String FINISHED_GOODS_WAREHOUSE = "71000000-0000-4000-8000-000000000003";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	SalesShipmentIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void shipsSalesOrderDeductsFinishedGoodsAndRejectsOvershipAndDuplicate() throws Exception {
		String orderId = createReleasedOrder(6);
		String orderLineId = jdbcTemplate.queryForObject(
				"select id from sales.order_lines where order_id = cast(? as uuid)", String.class, orderId);
		LocalDate shipDate = LocalDate.now().plusDays(2);

		String firstBody = shipmentBody(orderId, orderLineId, FINISHED_GOODS_WAREHOUSE, shipDate, 2);
		MvcResult firstShipment = mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "sales-shipment-0001").contentType(MediaType.APPLICATION_JSON).content(firstBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SHIPPED"))
				.andExpect(jsonPath("$.totalShippedQuantity").value(2))
				.andExpect(jsonPath("$.lines[0].stockSummary").value(org.hamcrest.Matchers.containsString("WH-FG/FG-01")))
				.andReturn();
		String firstShipmentId = field(firstShipment, "id");
		String firstShipmentLineId = com.jayway.jsonpath.JsonPath.parse(firstShipment.getResponse().getContentAsString()).read("$.lines[0].id");

		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "sales-shipment-0001").contentType(MediaType.APPLICATION_JSON).content(firstBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(firstShipmentId));

		mockMvc.perform(get("/api/v1/sales/orders/{id}", orderId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PARTIALLY_SHIPPED"))
				.andExpect(jsonPath("$.lines[0].deliveredQuantity").value(2));

		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "sales-shipment-over-0001").contentType(MediaType.APPLICATION_JSON)
				.content(shipmentBody(orderId, orderLineId, FINISHED_GOODS_WAREHOUSE, shipDate, 5)))
				.andExpect(status().isUnprocessableEntity());

		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "sales-shipment-0002").contentType(MediaType.APPLICATION_JSON)
				.content(shipmentBody(orderId, orderLineId, FINISHED_GOODS_WAREHOUSE, shipDate, 4)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalShippedQuantity").value(4));

		mockMvc.perform(get("/api/v1/sales/orders/{id}", orderId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SHIPPED"))
				.andExpect(jsonPath("$.lines[0].deliveredQuantity").value(6));

		Integer onHand = jdbcTemplate.queryForObject("""
				select on_hand_quantity from warehouse.stock_balances
				where warehouse_id = cast(? as uuid) and material_id = cast(? as uuid) and lot_number = 'LOT-GS-2608A' and quality_status = 'AVAILABLE'
				""", Integer.class, FINISHED_GOODS_WAREHOUSE, MATERIAL);
		Integer movements = jdbcTemplate.queryForObject("""
				select count(*) from warehouse.stock_movements
				where source_type = 'SALES_SHIPMENT_LINE' and source_id in (
				    select id from sales.shipment_lines where shipment_id in (select id from sales.shipments where sales_order_id = cast(? as uuid))
				)
				""", Integer.class, orderId);
		org.assertj.core.api.Assertions.assertThat(onHand).isEqualTo(6);
		org.assertj.core.api.Assertions.assertThat(movements).isEqualTo(2);
	}

	@Test
	@Transactional
	void rejectsShipmentWithoutPermission() throws Exception {
		String orderId = createReleasedOrder(1);
		String orderLineId = jdbcTemplate.queryForObject("select id from sales.order_lines where order_id = cast(? as uuid)", String.class, orderId);
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'PRODUCTION_OPERATOR' where id = '30000000-0000-4000-8000-000000000101'");
		try {
			mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "sales-shipment-denied-0001").contentType(MediaType.APPLICATION_JSON)
					.content(shipmentBody(orderId, orderLineId, FINISHED_GOODS_WAREHOUSE, LocalDate.now().plusDays(1), 1)))
					.andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where id = '30000000-0000-4000-8000-000000000101'");
		}
	}

	private String createReleasedOrder(int quantity) throws Exception {
		LocalDate date = LocalDate.now().plusDays(5);
		String body = """
				{"customerId":"%s","currency":"CNY","taxRate":0.13,"requestedDeliveryDate":"%s","promisedDeliveryDate":"%s","owner":"沈妍","lines":[{"materialId":"%s","quantity":%d,"unitPrice":28000}]}
				""".formatted(CUSTOMER, date, date, MATERIAL, quantity);
		MvcResult created = mockMvc.perform(post("/api/v1/sales/orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "shipment-test-order-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body))
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
						{"action":"%s","expectedVersion":%d,"comment":"发货测试"}
						""".formatted(action, version)))
				.andExpect(status().isOk());
	}

	private static String shipmentBody(String orderId, String lineId, String warehouseId, LocalDate plannedDate, Number quantity) {
		return """
				{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"自动化发货","lines":[{"orderLineId":"%s","shippedQuantity":%d}]}
				""".formatted(orderId, warehouseId, plannedDate, lineId, quantity);
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(name) + "\\\":\\\"([^\\\"]+)\\\"").matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}
}
