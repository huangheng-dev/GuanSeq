package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MrpSuggestionConversionIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	MrpSuggestionConversionIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	@Transactional
	void approvesAndConvertsProductionAndPurchaseSuggestionsIntoTraceableDrafts() throws Exception {
		LocalDate today = LocalDate.now();
		jdbcTemplate.update("update planning.independent_demands set quantity = 30, required_date = ? where id = cast(? as uuid)",
				today.plusDays(7), "53000000-0000-4000-8000-000000000001");
		jdbcTemplate.update("update planning.independent_demands set quantity = 1200, required_date = ? where id = cast(? as uuid)",
				today.plusDays(9), "53000000-0000-4000-8000-000000000003");

		mockMvc.perform(post("/api/v1/planning/mrp-runs").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "mrp-suggestion-source-0001").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"审核转单闭环\",\"horizonStart\":\"%s\",\"horizonEnd\":\"%s\"}"
						.formatted(today, today.plusDays(45))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

		mockMvc.perform(get("/api/v1/planning/mrp-suggestions")
				.with(httpBasic(USERNAME, PASSWORD)).param("size", "100"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[?(@.recommendationType == 'PRODUCTION' && @.decisionStatus == 'PROPOSED')]").exists())
			.andExpect(jsonPath("$.items[?(@.recommendationType == 'PURCHASE' && @.decisionStatus == 'PROPOSED')]").exists());
		String production = suggestionId("PRODUCTION");
		String purchase = suggestionId("PURCHASE");

		action(production, "APPROVE", 0,
				"产能与交期已确认", "mrp-suggestion-approve-production-0001");
		String productionBody = """
				{"expectedVersion":1,"plannedStartDate":"%s","plannedReceiptDate":"%s","workshop":"总装一车间","owner":"周启明"}
				""".formatted(today.plusDays(1), today.plusDays(7));
		convert(production, productionBody, "mrp-suggestion-convert-production-0001", "PRODUCTION_ORDER");

		action(purchase, "APPROVE", 0, "供应与价格边界已确认",
				"mrp-suggestion-approve-purchase-0001");
		String purchaseBody = """
				{"expectedVersion":1,"supplierId":"81000000-0000-4000-8000-000000000001","currency":"CNY","taxRate":0.13,"unitPrice":80,"requestedReceiptDate":"%s","buyer":"唐工"}
				""".formatted(today.plusDays(9));
		convert(purchase, purchaseBody, "mrp-suggestion-convert-purchase-0001", "PURCHASE_ORDER");

		convert(purchase, purchaseBody, "mrp-suggestion-convert-purchase-0001", "PURCHASE_ORDER");
		assertThat(jdbcTemplate.queryForObject("select count(*) from production.production_orders where source_type='MRP' and source_id=cast(? as uuid) and status='DRAFT'", Integer.class, production)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from procurement.purchase_orders where source_type='MRP' and source_id=cast(? as uuid) and status='DRAFT'", Integer.class, purchase)).isEqualTo(1);
	}

	private void action(String id, String action, int version, String comment, String requestId) throws Exception {
		mockMvc.perform(post("/api/v1/planning/mrp-suggestions/{id}/actions", id)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"%s\",\"expectedVersion\":%d,\"comment\":\"%s\"}".formatted(action, version, comment)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.decisionStatus").value("APPROVED"));
	}

	private void convert(String id, String body, String requestId, String orderType) throws Exception {
		mockMvc.perform(post("/api/v1/planning/mrp-suggestions/{id}/convert", id)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk()).andExpect(jsonPath("$.decisionStatus").value("CONVERTED"))
			.andExpect(jsonPath("$.convertedOrderType").value(orderType)).andExpect(jsonPath("$.convertedOrderNumber").isNotEmpty());
	}

	private String suggestionId(String type) {
		return jdbcTemplate.queryForObject("""
				select n.id::text from planning.mrp_run_net_requirements n
				join planning.mrp_runs r on r.id=n.run_id
				where r.name='审核转单闭环' and n.recommendation_type=?
				""", String.class, type);
	}
}
