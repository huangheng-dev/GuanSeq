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
class FinancePayableIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String SUPPLIER = "81000000-0000-4000-8000-000000000002";
	private static final String PACKAGING = "42000000-0000-4000-8000-000000000004";
	private static final String WAREHOUSE = "71000000-0000-4000-8000-000000000001";
	private static final String STORAGE_LOCATION = "72000000-0000-4000-8000-000000000001";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	FinancePayableIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void createsPayableFromAcceptedReceiptAndClosesItThroughIdempotentPayments() throws Exception {
		OrderData order = createReleasedAndReceivedOrder(5);
		LocalDate invoiceDate = LocalDate.now();
		String supplierInvoice = "SUP-INV-" + UUID.randomUUID();
		String invoiceRequest = "payable-invoice-" + UUID.randomUUID();
		MvcResult created = mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", invoiceRequest).contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(order, supplierInvoice, invoiceDate, 5)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.purchaseOrderId").value(order.orderId()))
				.andExpect(jsonPath("$.supplierInvoiceNumber").value(supplierInvoice))
				.andExpect(jsonPath("$.netAmount").value(60.00)).andExpect(jsonPath("$.taxAmount").value(7.80))
				.andExpect(jsonPath("$.grossAmount").value(67.80)).andExpect(jsonPath("$.status").value("OPEN"))
				.andReturn();
		String invoiceId = field(created, "id");

		mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", invoiceRequest).contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(order, supplierInvoice, invoiceDate, 5)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(invoiceId));
		mockMvc.perform(get("/api/v1/finance/payable-reference-data").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.orders[?(@.purchaseOrderId == '%s')].remainingAmount"
						.formatted(order.orderId())).value(org.hamcrest.Matchers.hasItem(0.0)));

		String firstPaymentRequest = "payable-payment-" + UUID.randomUUID();
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/payments", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", firstPaymentRequest).contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(0, invoiceDate, 20)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
				.andExpect(jsonPath("$.paidAmount").value(20.00)).andExpect(jsonPath("$.outstandingAmount").value(47.80));
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/payments", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", firstPaymentRequest).contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(0, invoiceDate, 20))).andExpect(status().isOk())
				.andExpect(jsonPath("$.payments.length()").value(1));
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/payments", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "payable-payment-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(1, invoiceDate, 47.8))).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.outstandingAmount").value(0.00));

		org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
				"select count(*) from finance.payable_events where invoice_id = cast(? as uuid)", Integer.class, invoiceId))
				.isEqualTo(3);
	}

	@Test
	@Transactional
	void rejectsOverInvoiceDuplicateSupplierInvoiceOverPaymentStaleVersionAndUnauthorizedWrite() throws Exception {
		OrderData order = createReleasedAndReceivedOrder(5);
		LocalDate date = LocalDate.now();
		String supplierInvoice = "SUP-INV-" + UUID.randomUUID();
		mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "payable-over-invoice-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(order, supplierInvoice, date, 6))).andExpect(status().isUnprocessableEntity());
		MvcResult created = mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "payable-invoice-guard-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(order, supplierInvoice, date, 5))).andExpect(status().isOk()).andReturn();
		String invoiceId = field(created, "id");

		OrderData secondOrder = createReleasedAndReceivedOrder(1);
		mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "payable-duplicate-supplier-invoice-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content(invoiceBody(secondOrder, supplierInvoice, date, 1)))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/payments", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "payable-over-payment-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(0, date, 100))).andExpect(status().isUnprocessableEntity());
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/payments", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "payable-payment-ok-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(0, date, 10))).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/finance/payable-invoices/{id}/payments", invoiceId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "payable-stale-version-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(0, date, 10))).andExpect(status().isConflict());

		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'PRODUCTION_OPERATOR' where id = '30000000-0000-4000-8000-000000000101'");
		try {
			mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "payable-denied-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
					.content(invoiceBody(secondOrder, "DENIED-" + UUID.randomUUID(), date, 1))).andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where id = '30000000-0000-4000-8000-000000000101'");
		}
	}

	private OrderData createReleasedAndReceivedOrder(Number quantity) throws Exception {
		LocalDate date = LocalDate.now().plusDays(5);
		String createRequest = "payable-order-" + UUID.randomUUID();
		MvcResult created = mockMvc.perform(post("/api/v1/procurement/orders").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", createRequest).contentType(MediaType.APPLICATION_JSON).content("""
						{"supplierId":"%s","currency":"CNY","taxRate":0.13,"requestedReceiptDate":"%s","promisedReceiptDate":"%s","buyer":"唐工","lines":[{"materialId":"%s","orderedQuantity":%s,"unitPrice":12}]}
						""".formatted(SUPPLIER, date, date, PACKAGING, quantity))).andExpect(status().isOk()).andReturn();
		String orderId = field(created, "id");
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", orderId).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SUBMIT\",\"expectedVersion\":0,\"comment\":\"应付测试\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", orderId).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVE\",\"expectedVersion\":1,\"comment\":\"应付测试\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", orderId).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RELEASE\",\"expectedVersion\":2,\"comment\":\"应付测试\"}"))
				.andExpect(status().isOk());
		String lineId = jdbcTemplate.queryForObject(
				"select id from procurement.purchase_order_lines where order_id = cast(? as uuid)", String.class, orderId);
		mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "payable-receipt-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"purchaseOrderId":"%s","warehouseId":"%s","locationId":"%s","note":"应付测试合格收货","lines":[{"orderLineId":"%s","receivedQuantity":%s,"lotNumber":"LOT-AP-%s"}]}
						""".formatted(orderId, WAREHOUSE, STORAGE_LOCATION, lineId, quantity, UUID.randomUUID())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"));
		return new OrderData(orderId, lineId);
	}

	private static String invoiceBody(OrderData order, String supplierInvoiceNumber, LocalDate date, Number quantity) {
		return """
				{"purchaseOrderId":"%s","supplierInvoiceNumber":"%s","invoiceDate":"%s","dueDate":"%s","lines":[{"purchaseOrderLineId":"%s","invoiceQuantity":%s}]}
				""".formatted(order.orderId(), supplierInvoiceNumber, date, date.plusDays(30), order.lineId(), quantity);
	}

	private static String paymentBody(long version, LocalDate date, Number amount) {
		return """
				{"expectedVersion":%d,"paymentDate":"%s","amount":%s,"paymentMethod":"BANK_TRANSFER","bankReference":"BANK-AP-001","note":"自动化核销"}
				""".formatted(version, date, amount);
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(name) + "\\\":\\\"([^\\\"]+)\\\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}

	private record OrderData(String orderId, String lineId) { }
}
