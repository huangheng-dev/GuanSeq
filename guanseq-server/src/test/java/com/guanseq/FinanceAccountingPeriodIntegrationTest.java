package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class FinanceAccountingPeriodIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String SALES_ORDER = "51000000-0000-4000-8000-000000000004";
	private static final String SALES_ORDER_LINE = "52000000-0000-4000-8000-000000000004";
	private static final String WAREHOUSE = "71000000-0000-4000-8000-000000000003";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	FinanceAccountingPeriodIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void listPeriodsReturnsTwelveOpenPeriods() throws Exception {
		mockMvc.perform(get("/api/v1/finance/accounting-periods").param("year", "2026")
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(12))
				.andExpect(jsonPath("$[0].fiscalYear").value(2026))
				.andExpect(jsonPath("$[0].fiscalPeriod").value(1))
				.andExpect(jsonPath("$[0].periodLabel").value("2026-01"))
				.andExpect(jsonPath("$[0].status").value("OPEN"))
				.andExpect(jsonPath("$[11].fiscalPeriod").value(12))
				.andExpect(jsonPath("$[11].periodLabel").value("2026-12"));
	}

	@Test
	@Transactional
	void createPeriodManuallyAndGetById() throws Exception {
		// 2028 年不在 V29 预生成范围内（2024-2027），手动创建
		MvcResult created = mockMvc.perform(post("/api/v1/finance/accounting-periods")
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "period-create-001")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"fiscalYear":2028,"fiscalPeriod":3}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fiscalYear").value(2028))
				.andExpect(jsonPath("$.fiscalPeriod").value(3))
				.andExpect(jsonPath("$.periodLabel").value("2028-03"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andReturn();

		String id = field(created, "id");

		mockMvc.perform(get("/api/v1/finance/accounting-periods/{id}", id).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.periodLabel").value("2028-03"));

		// 重复创建 → 409
		mockMvc.perform(post("/api/v1/finance/accounting-periods").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "period-create-dup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fiscalYear":2028,"fiscalPeriod":3}
						"""))
				.andExpect(status().isConflict());
	}

	@Test
	@Transactional
	void closeAndReopenPeriod() throws Exception {
		// 找到 2025-06 的期间 ID
		String periodId = findPeriodId(2025, 6);

		// 关账
		mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/close", periodId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "period-close-001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"))
				.andExpect(jsonPath("$.closedAt").exists())
				.andExpect(jsonPath("$.closedByName").exists());

		// 幂等：再次关账返回 200（不报错）
		mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/close", periodId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "period-close-002"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));

		// 重开缺少 reason → 400
		mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/reopen", periodId)
				.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reason":"补"}
						"""))
				.andExpect(status().isBadRequest());

		// 重开
		mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/reopen", periodId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "period-reopen-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reason":"补录上月遗漏凭证，需要重开"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.reopenedAt").exists())
				.andExpect(jsonPath("$.reopenReason").value("补录上月遗漏凭证，需要重开"));
	}

	@Test
	@Transactional
	void closedPeriodBlocksReceivableWrite() throws Exception {
		// 关账 2024-06（V29 预生成的期间）
		String periodId = findPeriodId(2024, 6);
		mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/close", periodId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "block-close-001"))
				.andExpect(status().isOk());

		// 先发货（今天）
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "block-ship-001").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"期间测试","lines":[{"orderLineId":"%s","shippedQuantity":1}]}
						""".formatted(SALES_ORDER, WAREHOUSE, LocalDate.now(), SALES_ORDER_LINE)))
				.andExpect(status().isOk());

		// 尝试用 2024-06 的日期开票 → 409 PERIOD_CLOSED
		LocalDate closedDate = LocalDate.of(2024, 6, 15);
		mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "block-inv-001").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(closedDate, 1)))
				.andExpect(status().isConflict())
				.andExpect(header().string("X-Error-Code", "PERIOD_CLOSED"))
				.andExpect(header().string("X-Period-Label", "2024-06"));

		// 重开期间
		mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/reopen", periodId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "block-reopen-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"reason":"测试验证后重开"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));

		// 重开后同日期开票 → 200
		mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "block-inv-002").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(closedDate, 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	@Transactional
	void autoCreatePeriodWhenDateNotPreSeeded() throws Exception {
		// 2029-01 不在 V29 预生成范围（2024-2027），首次写入时自动创建
		// 先发货
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "auto-ship-001").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"自动期间","lines":[{"orderLineId":"%s","shippedQuantity":1}]}
						""".formatted(SALES_ORDER, WAREHOUSE, LocalDate.now(), SALES_ORDER_LINE)))
				.andExpect(status().isOk());

		LocalDate futureDate = LocalDate.of(2029, 1, 20);
		mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "auto-inv-001").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(futureDate, 1)))
				.andExpect(status().isOk());

		// 验证期间被自动创建且为 OPEN
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from finance.accounting_periods where tenant_organization_id = '00000000-0000-4000-8000-000000000001' and fiscal_year = 2029 and fiscal_period = 1 and status = 'OPEN'",
				Integer.class);
		org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
	}

	@Test
	@Transactional
	void listWithoutYearReturnsCurrentYear() throws Exception {
		int currentYear = LocalDate.now().getYear();
		mockMvc.perform(get("/api/v1/finance/accounting-periods").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(12))
				.andExpect(jsonPath("$[0].fiscalYear").value(currentYear));
	}

	// ---- helpers ----

	private String findPeriodId(int year, int period) {
		return jdbcTemplate.queryForObject(
				"select id::text from finance.accounting_periods where tenant_organization_id = '00000000-0000-4000-8000-000000000001' and fiscal_year = ? and fiscal_period = ?",
				String.class, year, period);
	}

	private static String invoiceBody(LocalDate date, Number quantity) {
		return """
				{"salesOrderId":"%s","invoiceDate":"%s","dueDate":"%s","lines":[{"salesOrderLineId":"%s","invoiceQuantity":%s}]}
				""".formatted(SALES_ORDER, date, date.plusDays(30), SALES_ORDER_LINE, quantity);
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern
				.compile("\"" + java.util.regex.Pattern.quote(name) + "\":\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name);
		return matcher.group(1);
	}
}
