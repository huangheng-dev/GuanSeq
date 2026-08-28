package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;
import com.jayway.jsonpath.JsonPath;
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

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SalesReturnIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String CUSTOMER = "41000000-0000-4000-8000-000000000001";
	private static final String MATERIAL = "42000000-0000-4000-8000-000000000001";
	private static final String WAREHOUSE = "71000000-0000-4000-8000-000000000003";
	private static final String LOCATION = "72000000-0000-4000-8000-000000000004";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	SalesReturnIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void receivesReturnIntoInspectionAndSplitsQualityStock() throws Exception {
		OrderContext order = createAndShipOrder(5, "sales-return-quality");
		MvcResult created = createReturn(order, 3, "sales-return-create-quality");
		String returnId = field(created, "id");
		String returnLineId = JsonPath.parse(created.getResponse().getContentAsString()).read("$.lines[0].id");
		mockMvc.perform(post("/api/v1/sales/returns").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "sales-return-create-quality").contentType(MediaType.APPLICATION_JSON)
				.content(createBody(order, 3))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(returnId));

		String receiveBody = """
				{"action":"RECEIVE","expectedVersion":0,"reason":"客户退回实物到仓待检","warehouseId":"%s","locationId":"%s","lines":[{"returnLineId":"%s","lotNumber":"LOT-RETURN-001"}]}
				""".formatted(WAREHOUSE, LOCATION, returnLineId);
		MvcResult received = act(returnId, "sales-return-receive-quality", receiveBody)
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"))
				.andExpect(jsonPath("$.lines[0].receivedQuantity").value(3)).andReturn();
		long receivedVersion = ((Number) JsonPath.parse(received.getResponse().getContentAsString()).read("$.version")).longValue();
		act(returnId, "sales-return-receive-quality", receiveBody).andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(receivedVersion));

		mockMvc.perform(get("/api/v1/sales/orders/{id}", order.id()).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIALLY_RETURNED"))
				.andExpect(jsonPath("$.lines[0].deliveredQuantity").value(5))
				.andExpect(jsonPath("$.lines[0].returnedQuantity").value(3))
				.andExpect(jsonPath("$.lines[0].netDeliveredQuantity").value(2));

		act(returnId, "sales-return-inspect-quality", """
				{"action":"INSPECT","expectedVersion":%d,"reason":"退货检验完成并按结果分流","lines":[{"returnLineId":"%s","acceptedQuantity":2,"rejectedQuantity":1}]}
				""".formatted(receivedVersion, returnLineId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.lines[0].acceptedQuantity").value(2))
				.andExpect(jsonPath("$.lines[0].rejectedQuantity").value(1));

		assertThat(stock("INSPECTION", "LOT-RETURN-001")).isZero();
		assertThat(stock("AVAILABLE", "LOT-RETURN-001")).isEqualTo(2);
		assertThat(stock("BLOCKED", "LOT-RETURN-001")).isEqualTo(1);
		assertThat(movementCount(returnId)).isEqualTo(4);

		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "sales-return-replacement-shipment").contentType(MediaType.APPLICATION_JSON).content("""
						{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"客户退货后的替换发货","lines":[{"orderLineId":"%s","shippedQuantity":3}]}
						""".formatted(order.id(), WAREHOUSE, LocalDate.now().plusDays(2), order.lineId())))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/sales/orders/{id}", order.id()).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"))
				.andExpect(jsonPath("$.lines[0].deliveredQuantity").value(8))
				.andExpect(jsonPath("$.lines[0].returnedQuantity").value(3))
				.andExpect(jsonPath("$.lines[0].netDeliveredQuantity").value(5));
	}

	@Test
	@Transactional
	void reversesUninspectedReceiptAndRejectsStaleOrExcessReturn() throws Exception {
		OrderContext order = createAndShipOrder(2, "sales-return-reverse");
		mockMvc.perform(post("/api/v1/sales/returns").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "sales-return-excess").contentType(MediaType.APPLICATION_JSON)
				.content(createBody(order, 3))).andExpect(status().isUnprocessableEntity());

		MvcResult created = createReturn(order, 1, "sales-return-create-reverse");
		String returnId = field(created, "id");
		String lineId = JsonPath.parse(created.getResponse().getContentAsString()).read("$.lines[0].id");
		MvcResult received = act(returnId, "sales-return-receive-reverse", """
				{"action":"RECEIVE","expectedVersion":0,"reason":"客户退回实物等待确认","warehouseId":"%s","locationId":"%s","lines":[{"returnLineId":"%s","lotNumber":"LOT-RETURN-REV"}]}
				""".formatted(WAREHOUSE, LOCATION, lineId)).andExpect(status().isOk()).andReturn();
		long version = ((Number) JsonPath.parse(received.getResponse().getContentAsString()).read("$.version")).longValue();

		act(returnId, "sales-return-stale-reverse", """
				{"action":"REVERSE_RECEIPT","expectedVersion":0,"reason":"使用旧版本尝试冲回"}
				""").andExpect(status().isConflict());
		act(returnId, "sales-return-valid-reverse", """
				{"action":"REVERSE_RECEIPT","expectedVersion":%d,"reason":"仓库核实为误收并冲回"}
				""".formatted(version)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVERSED"));

		assertThat(stock("INSPECTION", "LOT-RETURN-REV")).isZero();
		mockMvc.perform(get("/api/v1/sales/orders/{id}", order.id()).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"))
				.andExpect(jsonPath("$.lines[0].returnedQuantity").value(0))
				.andExpect(jsonPath("$.lines[0].netDeliveredQuantity").value(2));
	}

	@Test
	@Transactional
	void rejectsReturnAuthorizationWithoutPermission() throws Exception {
		OrderContext order = createAndShipOrder(1, "sales-return-permission");
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'PRODUCTION_OPERATOR' where id = '30000000-0000-4000-8000-000000000101'");
		try {
			mockMvc.perform(post("/api/v1/sales/returns").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "sales-return-permission-denied").contentType(MediaType.APPLICATION_JSON)
					.content(createBody(order, 1))).andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where id = '30000000-0000-4000-8000-000000000101'");
		}
	}

	private OrderContext createAndShipOrder(int quantity, String requestPrefix) throws Exception {
		LocalDate date = LocalDate.now().plusDays(5);
		MvcResult created = mockMvc.perform(post("/api/v1/sales/orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestPrefix + "-order").contentType(MediaType.APPLICATION_JSON).content("""
						{"customerId":"%s","currency":"CNY","taxRate":0.13,"requestedDeliveryDate":"%s","promisedDeliveryDate":"%s","owner":"沈妍","lines":[{"materialId":"%s","quantity":%d,"unitPrice":28000}]}
						""".formatted(CUSTOMER, date, date, MATERIAL, quantity))).andExpect(status().isOk()).andReturn();
		String orderId = field(created, "id");
		String orderLineId = JsonPath.parse(created.getResponse().getContentAsString()).read("$.lines[0].id");
		orderAction(orderId, "SUBMIT", 0); orderAction(orderId, "APPROVE", 1); orderAction(orderId, "RELEASE", 2);
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestPrefix + "-shipment").contentType(MediaType.APPLICATION_JSON).content("""
						{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"退货测试发货","lines":[{"orderLineId":"%s","shippedQuantity":%d}]}
						""".formatted(orderId, WAREHOUSE, LocalDate.now().plusDays(1), orderLineId, quantity)))
				.andExpect(status().isOk());
		return new OrderContext(orderId, orderLineId, 4);
	}

	private MvcResult createReturn(OrderContext order, int quantity, String requestId) throws Exception {
		return mockMvc.perform(post("/api/v1/sales/returns").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON).content(createBody(order, quantity)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_RECEIPT")).andReturn();
	}

	private String createBody(OrderContext order, int quantity) {
		return """
				{"salesOrderId":"%s","expectedOrderVersion":%d,"returnDate":"%s","reason":"客户反馈质量异常申请退货","note":"自动化闭环","lines":[{"orderLineId":"%s","returnQuantity":%d}]}
				""".formatted(order.id(), order.version(), LocalDate.now(), order.lineId(), quantity);
	}

	private org.springframework.test.web.servlet.ResultActions act(String returnId, String requestId, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/sales/returns/{id}/actions", returnId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void orderAction(String id, String action, long version) throws Exception {
		mockMvc.perform(post("/api/v1/sales/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"action":"%s","expectedVersion":%d,"comment":"销售退货测试"}
						""".formatted(action, version))).andExpect(status().isOk());
	}

	private int stock(String qualityStatus, String lotNumber) {
		Integer quantity = jdbcTemplate.queryForObject("""
				select coalesce(sum(on_hand_quantity), 0) from warehouse.stock_balances
				where warehouse_id = cast(? as uuid) and location_id = cast(? as uuid)
				and material_id = cast(? as uuid) and quality_status = ? and lot_number = ?
				""", Integer.class, WAREHOUSE, LOCATION, MATERIAL, qualityStatus, lotNumber);
		return quantity == null ? 0 : quantity;
	}

	private int movementCount(String returnId) {
		return jdbcTemplate.queryForObject("select count(*) from warehouse.stock_movements where source_type = 'SALES_RETURN_LINE' and source_id = cast(? as uuid)", Integer.class, returnId);
	}

	private static String field(MvcResult result, String name) throws Exception {
		return JsonPath.parse(result.getResponse().getContentAsString()).read("$." + name);
	}

	private record OrderContext(String id, String lineId, long version) { }
}
