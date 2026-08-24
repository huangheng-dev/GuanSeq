package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;

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
class PrimaryBusinessLoopIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String CUSTOMER = "41000000-0000-4000-8000-000000000001";
	private static final String FINISHED_MATERIAL = "42000000-0000-4000-8000-000000000001";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;
	private final EntityManager entityManager;

	PrimaryBusinessLoopIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate, @Autowired EntityManager entityManager) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
		this.entityManager = entityManager;
	}

	@Test
	@Transactional
	void carriesReleasedSalesDemandThroughMrpIntoTraceableProductionDraft() throws Exception {
		LocalDate today = LocalDate.now();
		LocalDate deliveryDate = today.plusDays(7);
		jdbcTemplate.update("update planning.independent_demands set status = 'CANCELLED' where status = 'ACTIVE'");

		MvcResult createdOrder = mockMvc.perform(post("/api/v1/sales/orders")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "primary-loop-sales-create-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"customerId":"%s","currency":"CNY","taxRate":0.13,"requestedDeliveryDate":"%s","promisedDeliveryDate":"%s","owner":"沈妍","lines":[{"materialId":"%s","quantity":30,"unitPrice":28000}]}
							""".formatted(CUSTOMER, deliveryDate, deliveryDate, FINISHED_MATERIAL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andReturn();
		String salesOrderId = extractId(createdOrder);

		salesAction(salesOrderId, "SUBMIT", 0, "primary-loop-sales-submit-0001", "PENDING_APPROVAL");
		salesAction(salesOrderId, "APPROVE", 1, "primary-loop-sales-approve-0001", "APPROVED");
		salesAction(salesOrderId, "RELEASE", 2, "primary-loop-sales-release-0001", "RELEASED");

		String demandId = jdbcTemplate.queryForObject("""
				select id::text from planning.independent_demands
				where source_type = 'SALES_ORDER' and source_id = cast(? as uuid) and status = 'ACTIVE'
				""", String.class, salesOrderId);
		assertThat(demandId).isNotBlank();

		MvcResult mrpResult = mockMvc.perform(post("/api/v1/planning/mrp-runs")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "primary-loop-mrp-run-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"销售到生产纵向闭环","horizonStart":"%s","horizonEnd":"%s"}
							""".formatted(today, today.plusDays(45))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.demandCount").value(1))
				.andExpect(jsonPath("$.demands[?(@.demandId == '%s' && @.sourceType == 'SALES_ORDER')]".formatted(demandId)).exists())
				.andExpect(jsonPath("$.netRequirements[?(@.materialCode == 'GS-800' && @.recommendationType == 'PRODUCTION')]" ).exists())
				.andReturn();
		String mrpRunId = extractId(mrpResult);
		String suggestionId = jdbcTemplate.queryForObject("""
				select id::text from planning.mrp_run_net_requirements
				where run_id = cast(? as uuid) and material_code = 'GS-800' and recommendation_type = 'PRODUCTION'
				""", String.class, mrpRunId);

		mockMvc.perform(post("/api/v1/planning/mrp-suggestions/{id}/actions", suggestionId)
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "primary-loop-suggestion-approve-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"APPROVE\",\"expectedVersion\":0,\"comment\":\"交期与产能已确认\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.decisionStatus").value("APPROVED"));

		mockMvc.perform(post("/api/v1/planning/mrp-suggestions/{id}/convert", suggestionId)
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "primary-loop-suggestion-convert-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"expectedVersion":1,"plannedStartDate":"%s","plannedReceiptDate":"%s","workshop":"总装一车间","owner":"周启明"}
							""".formatted(today.plusDays(1), deliveryDate)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.decisionStatus").value("CONVERTED"))
				.andExpect(jsonPath("$.convertedOrderType").value("PRODUCTION_ORDER"))
				.andExpect(jsonPath("$.convertedOrderNumber").isNotEmpty());

		entityManager.flush();
		Integer linkedDrafts = jdbcTemplate.queryForObject("""
				select count(*) from production.production_orders
				where source_type = 'MRP' and source_id = cast(? as uuid) and status = 'DRAFT'
				""", Integer.class, suggestionId);
		Integer conversionEvidence = jdbcTemplate.queryForObject("""
				select count(*) from planning.mrp_suggestion_events
				where suggestion_id = cast(? as uuid) and action = 'CONVERT' and request_id = ?
				""", Integer.class, suggestionId, "primary-loop-suggestion-convert-0001");
		assertThat(linkedDrafts).isEqualTo(1);
		assertThat(conversionEvidence).isEqualTo(1);
	}

	private void salesAction(String orderId, String action, long version, String requestId, String expectedStatus)
			throws Exception {
		mockMvc.perform(post("/api/v1/sales/orders/{id}/actions", orderId)
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"%s\",\"expectedVersion\":%d,\"comment\":\"纵向闭环回归\"}"
							.formatted(action, version)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(expectedStatus));
	}

	private static String extractId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		var matcher = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(body);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + body);
		return matcher.group(1);
	}
}
