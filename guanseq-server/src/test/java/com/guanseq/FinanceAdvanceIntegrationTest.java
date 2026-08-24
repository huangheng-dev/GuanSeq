package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;

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
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FinanceAdvanceIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String CUSTOMER = "41000000-0000-4000-8000-000000000001";
	private static final String SUPPLIER = "81000000-0000-4000-8000-000000000002";
	private static final String SALES_ORDER = "51000000-0000-4000-8000-000000000004";
	private static final String SALES_ORDER_LINE = "52000000-0000-4000-8000-000000000004";
	private static final String WAREHOUSE_OUT = "71000000-0000-4000-8000-000000000003";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;
	private final EntityManager entityManager;

	FinanceAdvanceIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate,
			@Autowired EntityManager entityManager) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
		this.entityManager = entityManager;
	}

	@Test
	@Transactional
	void registersAdvanceAndAppliesOnInvoiceThenRefunds() throws Exception {
		LocalDate today = LocalDate.now();

		// 1. Register advance receipt 80000 (invoice gross ~67800, so partial remains)
		MvcResult advance = mockMvc.perform(post("/api/v1/finance/advances")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-reg-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"RECEIVABLE","partyId":"%s","advanceDate":"%s","totalAmount":80000,"note":"客户预付货款"}
						""".formatted(CUSTOMER, today)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.totalAmount").value(80000))
				.andExpect(jsonPath("$.availableBalance").value(80000))
				.andExpect(jsonPath("$.appliedAmount").value(0))
				.andReturn();
		String advanceId = field(advance, "id");

		// Idempotent
		mockMvc.perform(post("/api/v1/finance/advances")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-reg-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"RECEIVABLE","partyId":"%s","advanceDate":"%s","totalAmount":80000}
						""".formatted(CUSTOMER, today)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(advanceId));

		// 2. Ship 2 units then create invoice with advance offset
		mockMvc.perform(post("/api/v1/sales/shipments")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-ship-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"adv test","lines":[{"orderLineId":"%s","shippedQuantity":2}]}
						""".formatted(SALES_ORDER, WAREHOUSE_OUT, today, SALES_ORDER_LINE)))
				.andExpect(status().isOk());

		// Invoice gross is 2 * 30000 * 1.13 = 67800; advance 80000 covers full invoice
		MvcResult invoice = mockMvc.perform(post("/api/v1/finance/receivable-invoices")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-inv-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"salesOrderId":"%s","invoiceDate":"%s","dueDate":"%s","advanceId":"%s","lines":[{"salesOrderLineId":"%s","invoiceQuantity":2}]}
						""".formatted(SALES_ORDER, today, today.plusDays(30), advanceId, SALES_ORDER_LINE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.receivedAmount").value(67800))
				.andExpect(jsonPath("$.status").value("PAID"))
				.andReturn();

		// 3. Check advance is partially used (80000 - 67800 = 12200 remaining)
		mockMvc.perform(get("/api/v1/finance/advances/{id}", advanceId)
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PARTIALLY_USED"))
				.andExpect(jsonPath("$.appliedAmount").value(67800))
				.andExpect(jsonPath("$.availableBalance").value(12200))
				.andExpect(jsonPath("$.applications.length()").value(1))
				.andExpect(jsonPath("$.applications[0].invoiceNumber").exists());

		// 4. Refund exceeding available balance should fail
		mockMvc.perform(post("/api/v1/finance/advances/{id}/refund", advanceId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-refund-bad")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refundAmount":5000,"refundDate":"%s","reason":"超过可用余额应该报错"}
						""".formatted(today)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PARTIALLY_USED"))
				.andExpect(jsonPath("$.refundedAmount").value(5000))
				.andExpect(jsonPath("$.availableBalance").value(7200));

		entityManager.flush();
		Integer eventCount = jdbcTemplate.queryForObject(
				"select count(*) from finance.advance_events where request_id in ('adv-reg-001', 'adv-refund-bad')",
				Integer.class);
		Assertions.assertThat(eventCount).isEqualTo(2);
	}

	@Test
	@Transactional
	void registersPayableAdvanceAndRefundsPartial() throws Exception {
		LocalDate today = LocalDate.now();

		// Register payment advance 30000
		MvcResult advance = mockMvc.perform(post("/api/v1/finance/advances")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-pay-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"PAYABLE","partyId":"%s","advanceDate":"%s","totalAmount":30000,"note":"预付供应商"}
						""".formatted(SUPPLIER, today)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("PAYABLE"))
				.andExpect(jsonPath("$.partyType").value("SUPPLIER"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.availableBalance").value(30000))
				.andReturn();
		String advanceId = field(advance, "id");

		// Refund 10000 (reason too short -> 400)
		mockMvc.perform(post("/api/v1/finance/advances/{id}/refund", advanceId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-refund-short")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refundAmount":10000,"refundDate":"%s","reason":"错"}
						""".formatted(today)))
				.andExpect(status().isBadRequest());

		// Partial refund 10000
		mockMvc.perform(post("/api/v1/finance/advances/{id}/refund", advanceId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-refund-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refundAmount":10000,"refundDate":"%s","reason":"取消部分订单，退回预付款"}
						""".formatted(today)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PARTIALLY_USED"))
				.andExpect(jsonPath("$.refundedAmount").value(10000))
				.andExpect(jsonPath("$.availableBalance").value(20000))
				.andExpect(jsonPath("$.refunds.length()").value(1));

		// Refund remaining 20000 -> CLOSED
		mockMvc.perform(post("/api/v1/finance/advances/{id}/refund", advanceId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-refund-002")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refundAmount":20000,"refundDate":"%s","reason":"订单全部取消，退还剩余预付款"}
						""".formatted(today)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"))
				.andExpect(jsonPath("$.availableBalance").value(0));
	}

	@Test
	@Transactional
	void closedPeriodBlocksAdvanceOperations() throws Exception {
		LocalDate june = LocalDate.of(2026, 6, 15);

		String periodId = findPeriodId(2026, 6);
		mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/close", periodId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-close-001"))
				.andExpect(status().isOk());

		// Register in closed period -> 409
		mockMvc.perform(post("/api/v1/finance/advances")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-block-reg")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"RECEIVABLE","partyId":"%s","advanceDate":"%s","totalAmount":1000}
						""".formatted(CUSTOMER, june)))
				.andExpect(status().isConflict())
				.andExpect(header().string("X-Error-Code", "PERIOD_CLOSED"));
	}

	@Test
	@Transactional
	void rejectsAdvanceForInvalidParty() throws Exception {
		LocalDate today = LocalDate.now();

		// Random non-existent customer
		mockMvc.perform(post("/api/v1/finance/advances")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "adv-bad-party")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"RECEIVABLE","partyId":"%s","advanceDate":"%s","totalAmount":1000}
						""".formatted(UUID.randomUUID(), today)))
				.andExpect(status().isUnprocessableEntity());
	}

	// ---- helpers ----

	private String findPeriodId(int year, int period) {
		return jdbcTemplate.queryForObject(
				"select id::text from finance.accounting_periods where tenant_organization_id = '00000000-0000-4000-8000-000000000001' and fiscal_year = ? and fiscal_period = ?",
				String.class, year, period);
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern
				.compile("\"" + java.util.regex.Pattern.quote(name) + "\":\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}
}
