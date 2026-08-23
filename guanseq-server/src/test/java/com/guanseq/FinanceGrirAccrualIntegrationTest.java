package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class FinanceGrirAccrualIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String SUPPLIER = "81000000-0000-4000-8000-000000000002";
	private static final String PACKAGING = "42000000-0000-4000-8000-000000000004";
	private static final String WAREHOUSE = "71000000-0000-4000-8000-000000000001";
	private static final String STORAGE_LOCATION = "72000000-0000-4000-8000-000000000001";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	FinanceGrirAccrualIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void accruesReceivedNotInvoicedThenAutoReversesPriorOnNextRun() throws Exception {
		OrderData order = createReleasedAndReceivedOrder(3);
		LocalDate julyEnd = LocalDate.of(2026, 7, 31);
		LocalDate augEnd = LocalDate.of(2026, 8, 31);

		mockMvc.perform(get("/api/v1/finance/grir-accruals/preview")
				.param("year", "2026").param("period", "7")
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fiscalYear").value(2026))
				.andExpect(jsonPath("$.fiscalPeriod").value(7))
				.andExpect(jsonPath("$.totalNetAmount").value(36.00))
				.andExpect(jsonPath("$.lines.length()").value(1))
				.andExpect(jsonPath("$.lines[0].accruedQuantity").value(3))
				.andExpect(jsonPath("$.lines[0].netAmount").value(36.00));

		MvcResult july = mockMvc.perform(post("/api/v1/finance/grir-accruals/run")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "grir-jul-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content(runBody(2026, 7, julyEnd, "7 月暂估")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("POSTED"))
				.andExpect(jsonPath("$.totalNetAmount").value(36.00))
				.andExpect(jsonPath("$.lines.length()").value(1))
				.andReturn();
		String julyId = field(july, "id");

		// Idempotent by X-Request-Id
		mockMvc.perform(post("/api/v1/finance/grir-accruals/run")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "grir-jul-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content(runBody(2026, 7, julyEnd, "7 月暂估")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(julyId));

		// Register invoice for 2 of 3 in August
		LocalDate augDate = LocalDate.of(2026, 8, 15);
		mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "grir-inv-001").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(order, "SUP-GRIR-" + UUID.randomUUID(), augDate, 2)))
				.andExpect(status().isOk());

		// Preview August: remaining 1 x 12 = 12.00, prior = 36.00
		mockMvc.perform(get("/api/v1/finance/grir-accruals/preview")
				.param("year", "2026").param("period", "8")
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalNetAmount").value(12.00))
				.andExpect(jsonPath("$.priorAccrualId").value(julyId))
				.andExpect(jsonPath("$.priorAccrualAmount").value(36.00));

		// Run August: prior July auto-reversed
		MvcResult aug = mockMvc.perform(post("/api/v1/finance/grir-accruals/run")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "grir-aug-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content(runBody(2026, 8, augEnd, "8 月暂估")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("POSTED"))
				.andExpect(jsonPath("$.totalNetAmount").value(12.00))
				.andReturn();
		String augId = field(aug, "id");

		mockMvc.perform(get("/api/v1/finance/grir-accruals/{id}", julyId)
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVERSED"))
				.andExpect(jsonPath("$.reversedByAccrualId").value(augId))
				.andExpect(jsonPath("$.reversalDate").value(augEnd.toString()));

		mockMvc.perform(get("/api/v1/finance/grir-accruals").param("year", "2026")
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	@Transactional
	void zeroBalanceAccrualReversesPriorAndCreatesZeroAccrual() throws Exception {
		OrderData order = createReleasedAndReceivedOrder(1);
		LocalDate julyEnd = LocalDate.of(2026, 7, 31);
		LocalDate augEnd = LocalDate.of(2026, 8, 31);

		MvcResult july = mockMvc.perform(post("/api/v1/finance/grir-accruals/run")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "grir-zero-jul")
				.contentType(MediaType.APPLICATION_JSON).content(runBody(2026, 7, julyEnd, null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalNetAmount").value(12.00))
				.andReturn();
		String julyId = field(july, "id");

		LocalDate augDate = LocalDate.of(2026, 8, 10);
		mockMvc.perform(post("/api/v1/finance/payable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "grir-zero-inv").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(order, "SUP-ZERO-" + UUID.randomUUID(), augDate, 1)))
				.andExpect(status().isOk());

		MvcResult aug = mockMvc.perform(post("/api/v1/finance/grir-accruals/run")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "grir-zero-aug")
				.contentType(MediaType.APPLICATION_JSON).content(runBody(2026, 8, augEnd, null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalNetAmount").value(0.00))
				.andExpect(jsonPath("$.lines.length()").value(0))
				.andReturn();

		mockMvc.perform(get("/api/v1/finance/grir-accruals/{id}", julyId)
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVERSED"))
				.andExpect(jsonPath("$.reversedByAccrualId").value(field(aug, "id")));
	}

	@Test
	@Transactional
	void manualReverseRequiresReasonAndRejectsDuplicate() throws Exception {
		createReleasedAndReceivedOrder(2);
		LocalDate date = LocalDate.of(2026, 9, 30);

		MvcResult accrual = mockMvc.perform(post("/api/v1/finance/grir-accruals/run")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "grir-man-001")
				.contentType(MediaType.APPLICATION_JSON).content(runBody(2026, 9, date, null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("POSTED"))
				.andReturn();
		String id = field(accrual, "id");

		// Reason too short -> 400
		mockMvc.perform(post("/api/v1/finance/grir-accruals/{id}/reverse", id)
				.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reversalDate":"%s","reason":"错"}
						""".formatted(date)))
				.andExpect(status().isBadRequest());

		// Valid reversal
		mockMvc.perform(post("/api/v1/finance/grir-accruals/{id}/reverse", id)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "grir-man-rev-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reversalDate":"%s","reason":"录入金额有误，全额冲回"}
						""".formatted(date)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVERSED"))
				.andExpect(jsonPath("$.reversalReason").value("录入金额有误，全额冲回"));

		// Duplicate reversal -> 409
		mockMvc.perform(post("/api/v1/finance/grir-accruals/{id}/reverse", id)
				.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reversalDate":"%s","reason":"再次冲回尝试"}
						""".formatted(date)))
				.andExpect(status().isConflict());
	}

	@Test
	@Transactional
	void closedPeriodBlocksRun() throws Exception {
		createReleasedAndReceivedOrder(2);
		LocalDate juneEnd = LocalDate.of(2026, 6, 30);

		String periodId = findPeriodId(2026, 6);
		mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/close", periodId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "grir-close-001"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/finance/grir-accruals/run")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "grir-block-run")
				.contentType(MediaType.APPLICATION_JSON)
				.content(runBody(2026, 6, juneEnd, null)))
				.andExpect(status().isConflict())
				.andExpect(header().string("X-Error-Code", "PERIOD_CLOSED"))
				.andExpect(header().string("X-Period-Label", "2026-06"));
	}

	// ---- helpers ----

	private OrderData createReleasedAndReceivedOrder(Number quantity) throws Exception {
		LocalDate date = LocalDate.now().plusDays(5);
		MvcResult created = mockMvc.perform(post("/api/v1/procurement/orders")
				.with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "grir-order-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"supplierId":"%s","currency":"CNY","taxRate":0.13,"requestedReceiptDate":"%s","promisedReceiptDate":"%s","buyer":"唐工","lines":[{"materialId":"%s","orderedQuantity":%s,"unitPrice":12}]}
						""".formatted(SUPPLIER, date, date, PACKAGING, quantity)))
				.andExpect(status().isOk()).andReturn();
		String orderId = field(created, "id");
		String[][] actions = {{"SUBMIT", "0"}, {"APPROVE", "1"}, {"RELEASE", "2"}};
		for (String[] action : actions) {
			mockMvc.perform(post("/api/v1/procurement/orders/{id}/actions", orderId)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"%s\",\"expectedVersion\":%s,\"comment\":\"GRIR 测试\"}"
							.formatted(action[0], action[1])))
					.andExpect(status().isOk());
		}
		String lineId = jdbcTemplate.queryForObject(
				"select id from procurement.purchase_order_lines where order_id = cast(? as uuid)",
				String.class, orderId);
		mockMvc.perform(post("/api/v1/procurement/receipts").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "grir-receipt-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"purchaseOrderId":"%s","warehouseId":"%s","locationId":"%s","note":"GRIR 测试","lines":[{"orderLineId":"%s","receivedQuantity":%s,"lotNumber":"LOT-GRIR"}]}
						""".formatted(orderId, WAREHOUSE, STORAGE_LOCATION, lineId, quantity)))
				.andExpect(status().isOk());
		return new OrderData(orderId, lineId);
	}

	private String findPeriodId(int year, int period) {
		return jdbcTemplate.queryForObject(
				"select id::text from finance.accounting_periods where tenant_organization_id = '00000000-0000-4000-8000-000000000001' and fiscal_year = ? and fiscal_period = ?",
				String.class, year, period);
	}

	private static String runBody(int year, int period, LocalDate date, String note) {
		String noteField = note == null ? "null" : "\"" + note + "\"";
		return """
				{"fiscalYear":%d,"fiscalPeriod":%d,"accrualDate":"%s","note":%s}
				""".formatted(year, period, date, noteField);
	}

	private static String invoiceBody(OrderData order, String supplierInvoiceNumber, LocalDate date, Number quantity) {
		return """
				{"purchaseOrderId":"%s","supplierInvoiceNumber":"%s","invoiceDate":"%s","dueDate":"%s","lines":[{"purchaseOrderLineId":"%s","invoiceQuantity":%s}]}
				""".formatted(order.orderId(), supplierInvoiceNumber, date, date.plusDays(30),
				order.lineId(), quantity);
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern
				.compile("\"" + java.util.regex.Pattern.quote(name) + "\":\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}

	private record OrderData(String orderId, String lineId) { }
}
