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
class FinanceReceivableIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String ORDER = "51000000-0000-4000-8000-000000000004";
	private static final String ORDER_LINE = "52000000-0000-4000-8000-000000000004";
	private static final String WAREHOUSE = "71000000-0000-4000-8000-000000000003";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	FinanceReceivableIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void createsPartialInvoiceAndClosesItThroughTwoIdempotentReceipts() throws Exception {
		shipTwoUnits("receivable-shipment-0001");
		LocalDate invoiceDate = LocalDate.now();
		MvcResult created = mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-invoice-0001").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(invoiceDate, 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.orderNumber").value("SO-260815-004"))
				.andExpect(jsonPath("$.netAmount").value(30000.00)).andExpect(jsonPath("$.taxAmount").value(3900.00))
				.andExpect(jsonPath("$.grossAmount").value(33900.00)).andExpect(jsonPath("$.outstandingAmount").value(33900.00))
				.andExpect(jsonPath("$.status").value("OPEN")).andReturn();
		String invoiceId = field(created, "id");

		mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-invoice-0001").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(invoiceDate, 1))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(invoiceId));
		mockMvc.perform(get("/api/v1/finance/receivable-reference-data").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.orders[?(@.salesOrderId == '%s')].lines[0].remainingQuantity".formatted(ORDER))
						.value(org.hamcrest.Matchers.hasItem(1.0)));

		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/receipts", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-receipt-0001").contentType(MediaType.APPLICATION_JSON)
				.content(receiptBody(0, invoiceDate, 10000)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
				.andExpect(jsonPath("$.receivedAmount").value(10000.00)).andExpect(jsonPath("$.outstandingAmount").value(23900.00));
		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/receipts", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-receipt-0001").contentType(MediaType.APPLICATION_JSON)
				.content(receiptBody(0, invoiceDate, 10000))).andExpect(status().isOk())
				.andExpect(jsonPath("$.receipts.length()").value(1));
		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/receipts", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-receipt-0002").contentType(MediaType.APPLICATION_JSON)
				.content(receiptBody(1, invoiceDate, 23900))).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.outstandingAmount").value(0.00));

		org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
				"select count(*) from finance.receivable_events where invoice_id = cast(? as uuid)", Integer.class, invoiceId)).isEqualTo(3);
	}

	@Test
	@Transactional
	void rejectsOverInvoiceOverPaymentStaleVersionAndUnauthorizedWrite() throws Exception {
		shipTwoUnits("receivable-shipment-0002");
		LocalDate date = LocalDate.now();
		mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-over-invoice").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(date, 3))).andExpect(status().isUnprocessableEntity());
		MvcResult created = mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-invoice-guard").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(date, 1))).andExpect(status().isOk()).andReturn();
		String invoiceId = field(created, "id");
		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/receipts", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-over-payment").contentType(MediaType.APPLICATION_JSON)
				.content(receiptBody(0, date, 40000))).andExpect(status().isUnprocessableEntity());
		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/receipts", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-payment-ok").contentType(MediaType.APPLICATION_JSON)
				.content(receiptBody(0, date, 100))).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/finance/receivable-invoices/{id}/receipts", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "receivable-stale-version").contentType(MediaType.APPLICATION_JSON)
				.content(receiptBody(0, date, 100))).andExpect(status().isConflict());

		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'PRODUCTION_OPERATOR' where id = '30000000-0000-4000-8000-000000000101'");
		try {
			mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "receivable-denied").contentType(MediaType.APPLICATION_JSON)
					.content(invoiceBody(date, 1))).andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where id = '30000000-0000-4000-8000-000000000101'");
		}
	}

	private void shipTwoUnits(String requestId) throws Exception {
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"应收自动化测试","lines":[{"orderLineId":"%s","shippedQuantity":2}]}
						""".formatted(ORDER, WAREHOUSE, LocalDate.now(), ORDER_LINE))).andExpect(status().isOk());
	}

	private static String invoiceBody(LocalDate date, Number quantity) {
		return """
				{"salesOrderId":"%s","invoiceDate":"%s","dueDate":"%s","lines":[{"salesOrderLineId":"%s","invoiceQuantity":%s}]}
				""".formatted(ORDER, date, date.plusDays(30), ORDER_LINE, quantity);
	}

	private static String receiptBody(long version, LocalDate date, Number amount) {
		return """
				{"expectedVersion":%d,"receiptDate":"%s","amount":%s,"paymentMethod":"BANK_TRANSFER","bankReference":"BANK-001","note":"自动化核销"}
				""".formatted(version, date, amount);
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(name) + "\\\":\\\"([^\\\"]+)\\\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}
}
