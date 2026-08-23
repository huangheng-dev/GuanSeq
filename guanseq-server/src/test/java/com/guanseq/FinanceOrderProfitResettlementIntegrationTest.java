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

/**
 * 订单利润冲销与恢复切片验收测试。覆盖：首次结算 SETTLED v1；红字发票过账后 IMPACTED；
 * resettle 生成 v2 SETTLED、旧版本 SUPERSEDED 且 supersedes_id 指向旧 ID；
 * 收入按蓝字发票净额 + 红字净额（负数）计算；重算原因 ≥4 字符；
 * 已关账期间 409 PERIOD_CLOSED；无权限 403；幂等；历史版本接口。
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FinanceOrderProfitResettlementIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String SALES_ORDER = "51000000-0000-4000-8000-000000000004";
	private static final String SALES_ORDER_LINE = "52000000-0000-4000-8000-000000000004";
	private static final String WAREHOUSE = "71000000-0000-4000-8000-000000000003";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	FinanceOrderProfitResettlementIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void creditNoteMarksSettlementImpactedAndResettleBuildsV2WithInvoiceNetRevenue() throws Exception {
		shipTwoUnits("reset-ship-0001");
		LocalDate date = LocalDate.now();

		// 1. 首次结算（无发票，收入按发货×单价 = 2 × 30000 = 60000）
		MvcResult first = mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", SALES_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-settle-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SETTLED"))
				.andExpect(jsonPath("$.settlementVersion").value(1))
				.andExpect(jsonPath("$.supersedesId").doesNotExist())
				.andReturn();
		String v1Id = field(first, "id");

		// 2. 开蓝字发票 1 件（含税 33900，净 30000）
		MvcResult inv = mockMvc.perform(post("/api/v1/finance/receivable-invoices").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "reset-inv-0001").contentType(MediaType.APPLICATION_JSON)
				.content(invoiceBody(date, 1)))
				.andExpect(status().isOk()).andReturn();
		String invoiceId = field(inv, "id");
		String invoiceLineId = fieldFromArray(inv, "lines", "id");

		// 3. 红字发票冲 1 件（净 -30000）。该订单已有 SETTLED 快照，应被标记为 IMPACTED
		mockMvc.perform(post("/api/v1/finance/receivable-credit-notes").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "reset-cn-0001").contentType(MediaType.APPLICATION_JSON)
				.content(creditNoteBody(invoiceId, invoiceLineId, date, 1)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/finance/order-profits/{id}", v1Id).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IMPACTED"))
				.andExpect(jsonPath("$.impactReason").value(org.hamcrest.Matchers.containsString("过账")));

		// 4. resettle 生成 v2 SETTLED，旧 v1 SUPERSEDED
		// 收入 = 蓝字净 30000 + 红字净 -30000 = 0；成本按 2 件发货归集（生产 MO-260815-012 已完工 2 件）
		MvcResult second = mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/resettle", SALES_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-resettle-0001")
				.contentType(MediaType.APPLICATION_JSON)
				.content(resettleBody("红字发票冲销后按发票净额重算", date, null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SETTLED"))
				.andExpect(jsonPath("$.settlementVersion").value(2))
				.andExpect(jsonPath("$.supersedesId").value(v1Id))
				.andExpect(jsonPath("$.revenue").value(0.00))
				.andExpect(jsonPath("$.materialCost").value(4240.00))
				.andExpect(jsonPath("$.grossProfit").value(-4840.00))
				.andReturn();
		String v2Id = field(second, "id");

		// 旧版本应为 SUPERSEDED
		mockMvc.perform(get("/api/v1/finance/order-profits/{id}", v1Id).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUPERSEDED"));

		// 台账列表默认只返回当前版本（v2），不返回 SUPERSEDED
		mockMvc.perform(get("/api/v1/finance/order-profits?query=SO-260815-004").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(v2Id))
				.andExpect(jsonPath("$.items.length()").value(1));

		// 历史版本按版本号倒序，v2 在前
		mockMvc.perform(get("/api/v1/finance/order-profits/{salesOrderId}/history", SALES_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(v2Id))
				.andExpect(jsonPath("$[0].settlementVersion").value(2))
				.andExpect(jsonPath("$[1].id").value(v1Id))
				.andExpect(jsonPath("$[1].settlementVersion").value(1))
				.andExpect(jsonPath("$[1].status").value("SUPERSEDED"));

		// RESETTLE 事件已写入
		Integer eventCount = jdbcTemplate.queryForObject(
				"select count(*) from finance.order_profit_events where settlement_id = cast(? as uuid) and action = 'RESETTLE'",
				Integer.class, UUID.fromString(v2Id));
		org.assertj.core.api.Assertions.assertThat(eventCount).isEqualTo(1);
	}

	@Test
	@Transactional
	void rejectsShortReasonAndClosedPeriodAndLacksPermission() throws Exception {
		shipTwoUnits("reset-ship-0002");
		LocalDate date = LocalDate.now();

		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", SALES_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-settle-0002"))
				.andExpect(status().isOk());

		// 原因 < 4 字符 → 422
		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/resettle", SALES_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-resettle-short")
				.contentType(MediaType.APPLICATION_JSON).content(resettleBody("少", date, null)))
				.andExpect(status().isUnprocessableEntity());

		// 关账 2024-06（V29 预生成的期间），重算日期落在该月 → 409 PERIOD_CLOSED
		String periodId = findPeriodId(2024, 6);
		mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/close", periodId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-close-001"))
				.andExpect(status().isOk());
		try {
			LocalDate closedDate = LocalDate.of(2024, 6, 15);
			mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/resettle", SALES_ORDER)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-resettle-closed")
					.contentType(MediaType.APPLICATION_JSON).content(resettleBody("期间已关账应被拒绝", closedDate, null)))
					.andExpect(status().isConflict())
					.andExpect(header().string("X-Error-Code", "PERIOD_CLOSED"))
					.andExpect(header().string("X-Period-Label", "2024-06"));
		} finally {
			mockMvc.perform(post("/api/v1/finance/accounting-periods/{id}/reopen", periodId)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-reopen-001")
					.contentType(MediaType.APPLICATION_JSON).content("""
							{"reason":"测试验证后重开期间"}
							"""))
					.andExpect(status().isOk());
		}

		// 无权限角色 → 403
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'PRODUCTION_OPERATOR' where id = '30000000-0000-4000-8000-000000000101'");
		try {
			mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/resettle", SALES_ORDER)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-resettle-denied")
					.contentType(MediaType.APPLICATION_JSON).content(resettleBody("无权限应被拒绝", date, null)))
					.andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code = 'ADMIN' where id = '30000000-0000-4000-8000-000000000101'");
		}
	}

	@Test
	@Transactional
	void resettleIsIdempotentByRequestId() throws Exception {
		shipTwoUnits("reset-ship-0003");
		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/settle", SALES_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-settle-0003"))
				.andExpect(status().isOk());

		MvcResult first = mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/resettle", SALES_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-resettle-idempotent")
				.contentType(MediaType.APPLICATION_JSON)
				.content(resettleBody("补录标准成本后刷新利润", LocalDate.now(), null)))
				.andExpect(status().isOk()).andReturn();
		String id = field(first, "id");

		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/resettle", SALES_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-resettle-idempotent")
				.contentType(MediaType.APPLICATION_JSON)
				.content(resettleBody("补录标准成本后刷新利润", LocalDate.now(), null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id));
	}

	@Test
	@Transactional
	void resettleWithoutSnapshotReturns404() throws Exception {
		shipTwoUnits("reset-ship-0004");
		mockMvc.perform(post("/api/v1/finance/order-profits/{salesOrderId}/resettle", SALES_ORDER)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "reset-resettle-nosnap")
				.contentType(MediaType.APPLICATION_JSON)
				.content(resettleBody("没有结算时重算应 404", LocalDate.now(), null)))
				.andExpect(status().isNotFound());
	}

	private String findPeriodId(int year, int month) {
		return jdbcTemplate.queryForObject(
				"select id::text from finance.accounting_periods where tenant_organization_id = '00000000-0000-4000-8000-000000000001' and fiscal_year = ? and fiscal_period = ?",
				String.class, year, month);
	}

	private void shipTwoUnits(String requestId) throws Exception {
		mockMvc.perform(post("/api/v1/sales/shipments").with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"salesOrderId":"%s","warehouseId":"%s","plannedShippingDate":"%s","note":"利润冲销测试","lines":[{"orderLineId":"%s","shippedQuantity":2}]}
						""".formatted(SALES_ORDER, WAREHOUSE, LocalDate.now(), SALES_ORDER_LINE))).andExpect(status().isOk());
	}

	private static String invoiceBody(LocalDate date, Number quantity) {
		return """
				{"salesOrderId":"%s","invoiceDate":"%s","dueDate":"%s","lines":[{"salesOrderLineId":"%s","invoiceQuantity":%s}]}
				""".formatted(SALES_ORDER, date, date.plusDays(30), SALES_ORDER_LINE, quantity);
	}

	private static String creditNoteBody(String invoiceId, String lineId, LocalDate date, Number quantity) {
		return """
				{"originalInvoiceId":"%s","creditNoteDate":"%s","dueDate":"%s","reason":"质量问题退货红冲","lines":[{"originalInvoiceLineId":"%s","creditQuantity":%s}]}
				""".formatted(invoiceId, date, date.plusDays(15), lineId, quantity);
	}

	private static String resettleBody(String reason, LocalDate date, Long expectedVersion) {
		String version = expectedVersion == null ? "null" : expectedVersion.toString();
		return """
				{"reason":"%s","settlementDate":"%s","expectedVersion":%s}
				""".formatted(reason, date, version);
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
