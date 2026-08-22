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
class FinancePayableCreditNoteIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String SUPPLIER = "81000000-0000-4000-8000-000000000002";
	private static final String PACKAGING = "42000000-0000-4000-8000-000000000004";
	private static final String WAREHOUSE = "71000000-0000-4000-8000-000000000001";
	private static final String STORAGE_LOCATION = "72000000-0000-4000-8000-000000000001";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	FinancePayableCreditNoteIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void fullRedToRefundToSettledThenReverseRestoresOpen() throws Exception {
		OrderData order = createReleasedAndReceivedOrder(2);
		LocalDate date = LocalDate.now();
		String supplierInvoice = "SUP-CN-" + UUID.randomUUID();

		// 1. 开票 2 件（含税 27.12 = 24 net + 3.12 tax）
		MvcResult created = mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-inv-0001").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(order, supplierInvoice, date, 2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.grossAmount").value(27.12))
				.andReturn();
		String invoiceId = field(created, "id");
		String invoiceLineId = fieldFromArray(created, "lines", "id");

		// 2. 全额付款 → PAID
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/payments", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-pay-0001").contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(0, date, 27.12)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"))
				.andExpect(jsonPath("$.paidAmount").value(27.12));

		// 3. 全额红冲 2 件 → CREDIT_PENDING, creditBalance = 27.12
		mockMvc.perform(post("/api/v1/finance/payable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-cn-0001").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.grossAmount").value(-27.12))
				.andExpect(jsonPath("$.status").value("POSTED"))
				.andExpect(jsonPath("$.supplierCreditNoteNumber").value("SUP-CN-001"));

		mockMvc.perform(get("/api/v1/finance/payable-invoices/{id}", invoiceId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CREDIT_PENDING"))
				.andExpect(jsonPath("$.creditBalance").value(27.12));

		// 4. 部分退款 10 → CREDIT_PENDING, creditBalance = 17.12
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/refunds", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-rf-0001").contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(2, date, 10)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditBalance").value(17.12))
				.andExpect(jsonPath("$.status").value("CREDIT_PENDING"));

		// 5. 超额退款 20 → 422
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/refunds", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-rf-over").contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(3, date, 20)))
				.andExpect(status().isUnprocessableEntity());

		// 6. 退款剩余 17.12 → SETTLED
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/refunds", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-rf-0002").contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(3, date, 17.12)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditBalance").value(0.00))
				.andExpect(jsonPath("$.status").value("SETTLED"));

		// 7. 反核销第二笔退款 → creditBalance 回补 17.12, CREDIT_PENDING
		String refund2Id = findPaymentIdByDirection(jdbcTemplate, invoiceId, "REFUND", 2);
		mockMvc.perform(post("/api/v1/finance/payables/payments/{id}/reverse", refund2Id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-rv-0001").contentType(MediaType.APPLICATION_JSON)
				.content(reverseBody(date)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditBalance").value(17.12))
				.andExpect(jsonPath("$.status").value("CREDIT_PENDING"));

		// 8. 重复反核销 → 409
		mockMvc.perform(post("/api/v1/finance/payables/payments/{id}/reverse", refund2Id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-rv-dup").contentType(MediaType.APPLICATION_JSON)
				.content(reverseBody(date)))
				.andExpect(status().isConflict());

		// 9. 反核销第一笔退款 → creditBalance = 27.12
		String refund1Id = findPaymentIdByDirection(jdbcTemplate, invoiceId, "REFUND", 1);
		mockMvc.perform(post("/api/v1/finance/payables/payments/{id}/reverse", refund1Id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-rv-0002").contentType(MediaType.APPLICATION_JSON)
				.content(reverseBody(date)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditBalance").value(27.12));

		// 10. 反核销原付款 → paidAmount = 0, outstanding = 27.12, CREDIT_PENDING
		String receiptId = findPaymentIdByDirection(jdbcTemplate, invoiceId, "PAYMENT", 1);
		mockMvc.perform(post("/api/v1/finance/payables/payments/{id}/reverse", receiptId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-rv-0003").contentType(MediaType.APPLICATION_JSON)
				.content(reverseBody(date)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paidAmount").value(0.00))
				.andExpect(jsonPath("$.outstandingAmount").value(27.12))
				.andExpect(jsonPath("$.status").value("CREDIT_PENDING"));

		// 验证审计事件
		Integer eventCount = jdbcTemplate.queryForObject(
				"select count(*) from finance.payable_events where invoice_id = cast(? as uuid)", Integer.class, invoiceId);
		org.assertj.core.api.Assertions.assertThat(eventCount).isGreaterThanOrEqualTo(6);
	}

	@Test
	@Transactional
	void rejectsOverCreditAndIdempotentCreditNote() throws Exception {
		OrderData order = createReleasedAndReceivedOrder(5);
		LocalDate date = LocalDate.now();
		String supplierInvoice = "SUP-CN-" + UUID.randomUUID();
		MvcResult created = mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-inv-0002").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(order, supplierInvoice, date, 2)))
				.andExpect(status().isOk()).andReturn();
		String invoiceId = field(created, "id");
		String invoiceLineId = fieldFromArray(created, "lines", "id");

		// 红冲 3 件（超过开票 2 件）→ 422
		mockMvc.perform(post("/api/v1/finance/payable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-over-cn").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 3)))
				.andExpect(status().isUnprocessableEntity());

		// 正常红冲 1 件
		mockMvc.perform(post("/api/v1/finance/payable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-cn-0002").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 1)))
				.andExpect(status().isOk());

		// 幂等：相同 requestId 返回同一红字发票
		mockMvc.perform(post("/api/v1/finance/payable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-cn-0002").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditNoteNumber").exists());

		// 再红冲 2 件（总共 3 > 2）→ 422
		mockMvc.perform(post("/api/v1/finance/payable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-cn-0003").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 2)))
				.andExpect(status().isUnprocessableEntity());
	}

	private OrderData createReleasedAndReceivedOrder(Number quantity) throws Exception {
		LocalDate date = LocalDate.now().plusDays(5);
		MvcResult created = mockMvc.perform(post("/api/v1/procurement/orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-order-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("""
						{"supplierId":"%s","currency":"CNY","taxRate":0.13,"requestedReceiptDate":"%s","promisedReceiptDate":"%s","buyer":"唐工","lines":[{"materialId":"%s","orderedQuantity":%s,"unitPrice":12}]}
						""".formatted(SUPPLIER, date, date, PACKAGING, quantity))).andExpect(status().isOk()).andReturn();
		String orderId = field(created, "id");
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", orderId).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SUBMIT\",\"expectedVersion\":0,\"comment\":\"应付红字测试\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", orderId).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVE\",\"expectedVersion\":1,\"comment\":\"应付红字测试\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", orderId).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RELEASE\",\"expectedVersion\":2,\"comment\":\"应付红字测试\"}"))
				.andExpect(status().isOk());
		String lineId = jdbcTemplate.queryForObject(
				"select id from procurement.purchase_order_lines where order_id = cast(? as uuid)", String.class, orderId);
		mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "apcn-receipt-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"purchaseOrderId":"%s","warehouseId":"%s","locationId":"%s","note":"应付红字测试","lines":[{"orderLineId":"%s","receivedQuantity":%s,"lotNumber":"LOT-APCN"}]}
						""".formatted(orderId, WAREHOUSE, STORAGE_LOCATION, lineId, quantity)))
				.andExpect(status().isOk());
		return new OrderData(orderId, lineId);
	}

	private static String invoiceBody(OrderData order, String supplierInvoiceNumber, LocalDate date, Number quantity) {
		return """
				{"purchaseOrderId":"%s","supplierInvoiceNumber":"%s","invoiceDate":"%s","dueDate":"%s","lines":[{"purchaseOrderLineId":"%s","invoiceQuantity":%s}]}
				""".formatted(order.orderId(), supplierInvoiceNumber, date, date.plusDays(30), order.lineId(), quantity);
	}

	private static String paymentBody(long version, LocalDate date, Number amount) {
		return """
				{"expectedVersion":%d,"paymentDate":"%s","amount":%s,"paymentMethod":"BANK_TRANSFER","bankReference":"BANK-APCN","note":"自动化付款"}
				""".formatted(version, date, amount);
	}

	private static String creditNoteBody(String invoiceId, String lineId, LocalDate date, Number quantity) {
		return """
				{"originalInvoiceId":"%s","supplierCreditNoteNumber":"SUP-CN-001","creditNoteDate":"%s","dueDate":"%s","reason":"质量问题退货红冲","lines":[{"originalInvoiceLineId":"%s","creditQuantity":%s}]}
				""".formatted(invoiceId, date, date.plusDays(15), lineId, quantity);
	}

	private static String refundBody(long version, LocalDate date, Number amount) {
		return """
				{"expectedVersion":%d,"refundDate":"%s","amount":%s,"paymentMethod":"BANK_TRANSFER","bankReference":"REF-APCN","note":"退款"}
				""".formatted(version, date, amount);
	}

	private static String reverseBody(LocalDate date) {
		return """
				{"reversalDate":"%s","reason":"操作有误，反核销原记录"}
				""".formatted(date);
	}

	private static String findPaymentIdByDirection(JdbcTemplate jdbc, String invoiceId, String direction, int ordinal) {
		return jdbc.queryForList("""
				select id from finance.payable_payments
				where invoice_id = cast(? as uuid) and direction = ? and status = 'POSTED'
				order by created_at
				""", String.class, invoiceId, direction).get(ordinal - 1).toString();
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(name) + "\\\":\\\"([^\\\"]+)\\\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}

	private static String fieldFromArray(MvcResult result, String arrayName, String fieldName) throws Exception {
		String body = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile(
				"\\\"" + arrayName + "\\\":\\[\\{[^}]*?\\\"" + fieldName + "\\\":\\\"([^\\\"]+)\\\"")
				.matcher(body);
		if (!matcher.find()) throw new AssertionError("响应数组 " + arrayName + " 缺少 " + fieldName);
		return matcher.group(1);
	}

	private record OrderData(String orderId, String lineId) { }
}
