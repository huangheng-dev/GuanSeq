package com.guanseq;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FinanceCreditNoteIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String SALES_ORDER = "51000000-0000-4000-8000-000000000004";
	private static final String SALES_ORDER_LINE = "52000000-0000-4000-8000-000000000004";
	private static final String WAREHOUSE = "71000000-0000-4000-8000-000000000003";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	FinanceCreditNoteIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void fullRedToRefundToSettledThenReverseRestoresOpen() throws Exception {
		shipTwoUnits("cn-ship-0001");
		LocalDate date = LocalDate.now();

		// 1. 开票 1 件（含税 33900）
		MvcResult created = mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-inv-0001").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(date, 1)))
				.andExpect(status().isOk()).andReturn();
		String invoiceId = field(created, "id");
		String invoiceLineId = fieldFromArray(created, "lines", "id");

		// 2. 全额收款 → PAID
		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/receipts", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-rc-0001").contentType(MediaType.APPLICATION_JSON)
				.content(receiptBody(0, date, 33900)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));

		// 3. 全额红冲 1 件 → CREDIT_PENDING, creditBalance = 33900
		MvcResult cnResult = mockMvc.perform(post("/api/v1/finance/receivable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-cn-0001").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.grossAmount").value(-33900.00))
				.andExpect(jsonPath("$.status").value("POSTED"))
				.andReturn();
		String creditNoteId = field(cnResult, "id");

		mockMvc.perform(get("/api/v1/finance/receivable-invoices/{id}", invoiceId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CREDIT_PENDING"))
				.andExpect(jsonPath("$.creditBalance").value(33900.00));

		// 4. 部分退款 10000 → CREDIT_PENDING, creditBalance = 23900
		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/refunds", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-rf-0001").contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(2, date, 10000)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditBalance").value(23900.00))
				.andExpect(jsonPath("$.status").value("CREDIT_PENDING"));

		// 5. 超额退款 24000 → 422
		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/refunds", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-rf-over").contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(3, date, 24000)))
				.andExpect(status().isUnprocessableEntity());

		// 6. 退款剩余 23900 → SETTLED
		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/refunds", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-rf-0002").contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(3, date, 23900)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditBalance").value(0.00))
				.andExpect(jsonPath("$.status").value("SETTLED"));

		// 7. 反核销第二笔退款 → creditBalance 回补 23900, CREDIT_PENDING
		String refund2Id = findReceiptIdByDirection(jdbcTemplate, invoiceId, "REFUND", 2);
		mockMvc.perform(post("/api/v1/finance/receivables/receipts/{id}/reverse", refund2Id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-rv-0001").contentType(MediaType.APPLICATION_JSON)
				.content(reverseBody(date)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditBalance").value(23900.00))
				.andExpect(jsonPath("$.status").value("CREDIT_PENDING"));

		// 8. 重复反核销 → 409
		mockMvc.perform(post("/api/v1/finance/receivables/receipts/{id}/reverse", refund2Id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-rv-dup").contentType(MediaType.APPLICATION_JSON)
				.content(reverseBody(date)))
				.andExpect(status().isConflict());

		// 9. 反核销第一笔退款 → creditBalance = 33900
		String refund1Id = findReceiptIdByDirection(jdbcTemplate, invoiceId, "REFUND", 1);
		mockMvc.perform(post("/api/v1/finance/receivables/receipts/{id}/reverse", refund1Id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-rv-0002").contentType(MediaType.APPLICATION_JSON)
				.content(reverseBody(date)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditBalance").value(33900.00));

		// 10. 反核销原收款 → receivedAmount = 0, OPEN
		String receiptId = findReceiptIdByDirection(jdbcTemplate, invoiceId, "RECEIPT", 1);
		mockMvc.perform(post("/api/v1/finance/receivables/receipts/{id}/reverse", receiptId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-rv-0003").contentType(MediaType.APPLICATION_JSON)
				.content(reverseBody(date)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.receivedAmount").value(0.00))
				.andExpect(jsonPath("$.outstandingAmount").value(33900.00))
				.andExpect(jsonPath("$.status").value("CREDIT_PENDING"));

		// 验证审计事件
		Integer eventCount = jdbcTemplate.queryForObject(
				"select count(*) from finance.receivable_events where invoice_id = cast(? as uuid)", Integer.class, invoiceId);
		org.assertj.core.api.Assertions.assertThat(eventCount).isGreaterThanOrEqualTo(6);
	}

	@Test
	@Transactional
	void rejectsOverCreditAndIdempotentCreditNote() throws Exception {
		shipTwoUnits("cn-ship-0002");
		LocalDate date = LocalDate.now();
		MvcResult created = mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-inv-0002").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(date, 2)))
				.andExpect(status().isOk()).andReturn();
		String invoiceId = field(created, "id");
		String invoiceLineId = fieldFromArray(created, "lines", "id");

		// 红冲 3 件（超过开票 2 件）→ 422
		mockMvc.perform(post("/api/v1/finance/receivable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-over-cn").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 3)))
				.andExpect(status().isUnprocessableEntity());

		// 正常红冲 1 件
		mockMvc.perform(post("/api/v1/finance/receivable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-cn-0002").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 1)))
				.andExpect(status().isOk());

		// 幂等：相同 requestId 返回同一红字发票
		mockMvc.perform(post("/api/v1/finance/receivable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-cn-0002").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditNoteNumber").exists());

		// 再红冲 2 件（总共 3 > 2）→ 422
		mockMvc.perform(post("/api/v1/finance/receivable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "cn-cn-0003").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 2)))
				.andExpect(status().isUnprocessableEntity());
	}

	private void shipTwoUnits(String requestId) throws Exception {
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"红字测试","lines":[{"orderLineId":"%s","shippedQuantity":2}]}
						""".formatted(SALES_ORDER, WAREHOUSE, LocalDate.now(), SALES_ORDER_LINE))).andExpect(status().isOk());
	}

	private static String invoiceBody(LocalDate date, Number quantity) {
		return """
				{"salesOrderId":"%s","invoiceDate":"%s","dueDate":"%s","lines":[{"salesOrderLineId":"%s","invoiceQuantity":%s}]}
				""".formatted(SALES_ORDER, date, date.plusDays(30), SALES_ORDER_LINE, quantity);
	}

	private static String receiptBody(long version, LocalDate date, Number amount) {
		return """
				{"expectedVersion":%d,"receiptDate":"%s","amount":%s,"paymentMethod":"BANK_TRANSFER","bankReference":"BANK-CN","note":"核销"}
				""".formatted(version, date, amount);
	}

	private static String creditNoteBody(String invoiceId, String lineId, LocalDate date, Number quantity) {
		return """
				{"originalInvoiceId":"%s","creditNoteDate":"%s","dueDate":"%s","reason":"质量问题退货红冲","lines":[{"originalInvoiceLineId":"%s","creditQuantity":%s}]}
				""".formatted(invoiceId, date, date.plusDays(15), lineId, quantity);
	}

	private static String refundBody(long version, LocalDate date, Number amount) {
		return """
				{"expectedVersion":%d,"refundDate":"%s","amount":%s,"paymentMethod":"BANK_TRANSFER","bankReference":"REF-CN","note":"退款"}
				""".formatted(version, date, amount);
	}

	private static String reverseBody(LocalDate date) {
		return """
				{"reversalDate":"%s","reason":"操作有误，反核销原记录"}
				""".formatted(date);
	}

	private static String findReceiptIdByDirection(JdbcTemplate jdbc, String invoiceId, String direction, int ordinal) {
		return jdbc.queryForList("""
				select id from finance.receivable_receipts
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
}
