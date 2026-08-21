package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ProductionQualityReceiptIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String ORDER_ID = "91000000-0000-4000-8000-000000000001";
	private static final String WAREHOUSE_ID = "71000000-0000-4000-8000-000000000003";
	private static final String LOCATION_ID = "72000000-0000-4000-8000-000000000004";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	ProductionQualityReceiptIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build(); this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void reportsInspectsAndReceivesAcceptedFinishedGoodsExactlyOnce() throws Exception {
		mockMvc.perform(post("/api/v1/production/orders/{id}/actions", ORDER_ID).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "execution-start-0001").contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"START\",\"expectedVersion\":0,\"comment\":\"齐套开工\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

		MvcResult reportResult = mockMvc.perform(post("/api/v1/production/work-reports").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "execution-report-0001").contentType(MediaType.APPLICATION_JSON)
				.content("{\"orderId\":\"%s\",\"quantity\":3,\"shiftName\":\"白班\",\"operatorName\":\"陈磊\",\"note\":\"总装完工送检\",\"expectedOrderVersion\":1}".formatted(ORDER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_INSPECTION"))
				.andExpect(jsonPath("$.inspectionStatus").value("PENDING")).andReturn();
		String reportId = field(reportResult, "id"); String inspectionId = field(reportResult, "inspectionId");

		mockMvc.perform(get("/api/v1/production/orders/{id}", ORDER_ID).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.reportedQuantity").value(3))
				.andExpect(jsonPath("$.reportableQuantity").value(3));

		mockMvc.perform(post("/api/v1/quality/final-inspections/{id}/complete", inspectionId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "quality-decision-0001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"acceptedQuantity\":2,\"rejectedQuantity\":1,\"inspector\":\"吴倩\",\"defectDescription\":\"一台端子扭矩不合格\",\"conclusion\":\"两台放行，一台返修\",\"expectedVersion\":0}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result").value("PARTIALLY_PASSED"));

		mockMvc.perform(get("/api/v1/production/work-reports/{id}", reportId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_FOR_RECEIPT"));

		String settlement = "{\"warehouseId\":\"%s\",\"locationId\":\"%s\",\"lotNumber\":\"LOT-RUNTIME-001\",\"expectedVersion\":1}"
				.formatted(WAREHOUSE_ID, LOCATION_ID);
		mockMvc.perform(post("/api/v1/production/work-reports/{id}/settle", reportId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "execution-settle-0001").contentType(MediaType.APPLICATION_JSON).content(settlement))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"))
				.andExpect(jsonPath("$.acceptedQuantity").value(2)).andExpect(jsonPath("$.rejectedQuantity").value(1))
				.andExpect(jsonPath("$.receiptWarehouse").value("成品仓"));

		mockMvc.perform(post("/api/v1/production/work-reports/{id}/settle", reportId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "execution-settle-0001").contentType(MediaType.APPLICATION_JSON).content(settlement))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"));

		mockMvc.perform(get("/api/v1/production/orders/{id}", ORDER_ID).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.completedQuantity").value(4))
				.andExpect(jsonPath("$.reportedQuantity").value(0)).andExpect(jsonPath("$.reportableQuantity").value(4));
		BigDecimalPair balance = jdbcTemplate.queryForObject("""
			select on_hand_quantity, (select count(*) from warehouse.stock_movements m
			where m.source_type = 'PRODUCTION_REPORT' and m.source_id = cast(? as uuid))
			from warehouse.stock_balances where lot_number = 'LOT-RUNTIME-001'
			""", (rs, row) -> new BigDecimalPair(rs.getBigDecimal(1).intValueExact(), rs.getInt(2)), reportId);
		assertThat(balance).isEqualTo(new BigDecimalPair(2, 1));
	}

	private static String field(MvcResult result, String name) throws Exception {
		var matcher = java.util.regex.Pattern.compile("\\\"" + name + "\\\":\\\"([^\\\"]+)\\\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) throw new AssertionError("响应缺少 " + name); return matcher.group(1);
	}
	private record BigDecimalPair(int quantity, int movementCount) { }
}
