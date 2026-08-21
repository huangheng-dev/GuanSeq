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
class ProcurementReceiptIncomingInspectionIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String RELEASED_ORDER = "82000000-0000-4000-8000-000000000001";
	private static final String RELEASED_LINE = "83000000-0000-4000-8000-000000000001";
	private static final String WAREHOUSE = "71000000-0000-4000-8000-000000000001";
	private static final String STORAGE_LOCATION = "72000000-0000-4000-8000-000000000001";
	private static final String IQC_LOCATION = "72000000-0000-4000-8000-000000000002";
	private static final String SUPPLIER = "81000000-0000-4000-8000-000000000002";
	private static final String PACKAGING = "42000000-0000-4000-8000-000000000004";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	ProcurementReceiptIncomingInspectionIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void receivesDirectCompletesIqcAndRejectsInvalidAndDuplicateRequests() throws Exception {
		String orderId = createReleasedPackagingOrder();
		String directLineId = jdbcTemplate.queryForObject("select id from procurement.purchase_order_lines where order_id = cast(? as uuid)", String.class, orderId);
		String directReceiptBody = receiptBody(orderId, directLineId, 5, WAREHOUSE, STORAGE_LOCATION, "LOT-PACK-001");
		mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receipt-direct-0001").contentType(MediaType.APPLICATION_JSON).content(directReceiptBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"))
				.andExpect(jsonPath("$.acceptedQuantity").value(5));
		mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receipt-direct-0001").contentType(MediaType.APPLICATION_JSON).content(directReceiptBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"));

		String iqcBody = receiptBody(RELEASED_ORDER, RELEASED_LINE, 10, WAREHOUSE, IQC_LOCATION, "LOT-BR-IQC-001");
		MvcResult receiptResult = mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receipt-iqc-0001").contentType(MediaType.APPLICATION_JSON).content(iqcBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_INSPECTION"))
				.andExpect(jsonPath("$.lines[0].inspectionId").exists()).andReturn();
		String receiptId = field(receiptResult, "id");
		String inspectionId = com.jayway.jsonpath.JsonPath.parse(receiptResult.getResponse().getContentAsString()).read("$.lines[0].inspectionId");

		mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receipt-over-0001").contentType(MediaType.APPLICATION_JSON)
				.content(receiptBody(RELEASED_ORDER, RELEASED_LINE, 1000, WAREHOUSE, IQC_LOCATION, "LOT-BR-OVER")))
				.andExpect(status().isUnprocessableEntity());

		mockMvc.perform(post("/api/v1/quality/incoming-inspections/{id}/complete", inspectionId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "iqc-complete-version-0001").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"acceptedQuantity":7,"rejectedQuantity":3,"inspector":"吴倩","defectDescription":"三件尺寸超差","conclusion":"七件放行，三件隔离","expectedVersion":999}
						"""))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/quality/incoming-inspections/{id}/complete", inspectionId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "iqc-complete-0001").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"acceptedQuantity":7,"rejectedQuantity":3,"inspector":"吴倩","defectDescription":"三件尺寸超差","conclusion":"七件放行，三件隔离","expectedVersion":0}
						"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result").value("PARTIALLY_PASSED"));

		mockMvc.perform(get("/api/v1/procurement/receipts/{id}", receiptId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
				.andExpect(jsonPath("$.acceptedQuantity").value(7)).andExpect(jsonPath("$.rejectedQuantity").value(3));
		mockMvc.perform(get("/api/v1/procurement/orders/{id}", RELEASED_ORDER).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].receivedQuantity").value(7));

		Integer movements = jdbcTemplate.queryForObject("""
				select count(*) from warehouse.stock_movements
				where source_type = 'PURCHASE_RECEIPT_LINE' and source_id = cast(? as uuid)
				""", Integer.class, receiptId);
		Integer available = jdbcTemplate.queryForObject("""
				select on_hand_quantity from warehouse.stock_balances
				where location_id = cast(? as uuid) and lot_number = 'LOT-BR-IQC-001' and quality_status = 'AVAILABLE'
				""", Integer.class, IQC_LOCATION);
		Integer blocked = jdbcTemplate.queryForObject("""
				select on_hand_quantity from warehouse.stock_balances
				where location_id = cast(? as uuid) and lot_number = 'LOT-BR-IQC-001' and quality_status = 'BLOCKED'
				""", Integer.class, IQC_LOCATION);
		org.assertj.core.api.Assertions.assertThat(movements).isEqualTo(4);
		org.assertj.core.api.Assertions.assertThat(available).isEqualTo(7);
		org.assertj.core.api.Assertions.assertThat(blocked).isEqualTo(3);
	}

	@Test
	@Transactional
	void rejectsReceiptWithoutPermission() throws Exception {
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'PRODUCTION_OPERATOR' where id = '30000000-0000-4000-8000-000000000103'");
		jdbcTemplate.update("update identity.user_workspace_preferences set current_workspace_id = '10000000-0000-4000-8000-000000000103' where user_id = '20000000-0000-4000-8000-000000000001'");
		try {
			mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "receipt-forbidden-0001").contentType(MediaType.APPLICATION_JSON)
					.content(receiptBody(RELEASED_ORDER, RELEASED_LINE, 1, WAREHOUSE, IQC_LOCATION, "LOT-DENY")))
					.andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where id = '30000000-0000-4000-8000-000000000103'");
			jdbcTemplate.update("update identity.user_workspace_preferences set current_workspace_id = '10000000-0000-4000-8000-000000000101' where user_id = '20000000-0000-4000-8000-000000000001'");
		}
	}

	private String createReleasedPackagingOrder() throws Exception {
		LocalDate date = LocalDate.now().plusDays(5);
		String body = """
				{"supplierId":"%s","currency":"CNY","taxRate":0.13,"requestedReceiptDate":"%s","promisedReceiptDate":"%s","buyer":"唐工","lines":[{"materialId":"%s","orderedQuantity":20,"unitPrice":12}]}
				""".formatted(SUPPLIER, date, date, PACKAGING);
		MvcResult created = mockMvc.perform(post("/api/v1/procurement/orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receipt-test-order-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andReturn();
		String id = field(created, "id");
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"action":"SUBMIT","expectedVersion":0,"comment":"收货测试"}"""))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"action":"APPROVE","expectedVersion":1,"comment":"收货测试"}"""))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"action":"RELEASE","expectedVersion":2,"comment":"收货测试"}"""))
				.andExpect(status().isOk());
		return id;
	}

	private static String receiptBody(String orderId, String lineId, Number quantity, String warehouseId, String locationId, String lot) {
		return """
				{"purchaseOrderId":"%s","warehouseId":"%s","locationId":"%s","note":"自动化收货","lines":[{"orderLineId":"%s","receivedQuantity":%d,"lotNumber":"%s"}]}
				""".formatted(orderId, warehouseId, locationId, lineId, quantity, lot);
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(name) + "\\\":\\\"([^\\\"]+)\\\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}
}
