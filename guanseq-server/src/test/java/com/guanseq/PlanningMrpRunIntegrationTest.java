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
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
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
class PlanningMrpRunIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;
	private final EntityManager entityManager;

	PlanningMrpRunIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate, @Autowired EntityManager entityManager) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
		this.entityManager = entityManager;
	}

	@Test
	@Transactional
	void freezesSupplyAndCompletesTimePhasedNettingWhenFactsAreReady() throws Exception {
		LocalDate today = LocalDate.now();
		jdbcTemplate.update("update planning.independent_demands set required_date = ? where id = cast(? as uuid)",
				today, "53000000-0000-4000-8000-000000000001");
		MvcResult created = mockMvc.perform(post("/api/v1/planning/mrp-runs")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "mrp-ready-check-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("月末交付窗口检查", today, today.plusDays(45))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.demandCount").value(2))
				.andExpect(jsonPath("$.demands[?(@.demandNumber == 'DMD-260815-001')]").exists())
				.andExpect(jsonPath("$.demands[?(@.demandNumber == 'DMD-260815-003')]").exists())
				.andExpect(jsonPath("$.demands[?(@.demandNumber == 'DMD-260815-002')]").doesNotExist())
				.andExpect(jsonPath("$.exceptions").isEmpty())
				.andExpect(jsonPath("$.exceptions[?(@.code == 'STOCK_POSITION_UNAVAILABLE')]").doesNotExist())
				.andExpect(jsonPath("$.supplies[?(@.materialCode == 'GS-800' && @.availableQuantity == 6)]").exists())
				.andExpect(jsonPath("$.supplies[?(@.materialCode == 'BR-6204' && @.availableQuantity == 400)]").exists())
				.andExpect(jsonPath("$.exceptions[?(@.code == 'BOM_UNAVAILABLE' && @.materialCode == 'GS-800')]").doesNotExist())
				.andExpect(jsonPath("$.exceptions[?(@.code == 'LEAD_TIME_UNAVAILABLE' && @.materialCode == 'BR-6204')]").doesNotExist())
				.andExpect(jsonPath("$.scheduledReceipts[?(@.sourceOrderNumber == 'PO-260815-001' && @.materialCode == 'BR-6204' && @.outstandingQuantity == 600)]").exists())
				.andExpect(jsonPath("$.scheduledReceipts[?(@.sourceOrderNumber == 'MO-260815-012' && @.sourceType == 'PRODUCTION_ORDER' && @.outstandingQuantity == 6)]").exists())
				.andExpect(jsonPath("$.netRequirements[?(@.materialCode == 'GS-800' && @.netQuantity == 0 && @.recommendationType == 'NONE')]").exists())
				.andExpect(jsonPath("$.netRequirements[?(@.materialCode == 'BR-6204' && @.netQuantity == 0 && @.scheduledReceiptConsumed == 400)]").exists())
				.andExpect(jsonPath("$.requestId").value("mrp-ready-check-0001"))
				.andReturn();

		String runId = extractId(created);
		mockMvc.perform(post("/api/v1/planning/mrp-runs")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "mrp-ready-check-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("重复提交不应改写原记录", today, today.plusDays(45))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(runId))
				.andExpect(jsonPath("$.name").value("月末交付窗口检查"));
		mockMvc.perform(get("/api/v1/planning/mrp-runs/{id}", runId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(runId));

		entityManager.flush();
		Integer snapshots = jdbcTemplate.queryForObject(
				"select count(*) from planning.mrp_run_demands where run_id = cast(? as uuid)", Integer.class, runId);
		Integer events = jdbcTemplate.queryForObject(
				"select count(*) from planning.mrp_run_events where run_id = cast(? as uuid) and to_status = 'COMPLETED'",
				Integer.class, runId);
		assertThat(snapshots).isEqualTo(2);
		Integer supplySnapshots = jdbcTemplate.queryForObject(
				"select count(*) from planning.mrp_run_supply_snapshots where run_id = cast(? as uuid)", Integer.class, runId);
		assertThat(supplySnapshots).isEqualTo(3);
		Integer receiptSnapshots = jdbcTemplate.queryForObject(
				"select count(*) from planning.mrp_run_scheduled_receipt_snapshots where run_id = cast(? as uuid)", Integer.class, runId);
		assertThat(receiptSnapshots).isEqualTo(2);
		Integer netResults = jdbcTemplate.queryForObject(
				"select count(*) from planning.mrp_run_net_requirements where run_id = cast(? as uuid)", Integer.class, runId);
		assertThat(netResults).isEqualTo(2);
		assertThat(events).isEqualTo(1);
		Integer idempotentRuns = jdbcTemplate.queryForObject(
				"select count(*) from planning.mrp_runs where tenant_organization_id = cast(? as uuid) and request_id = ?",
				Integer.class, "00000000-0000-4000-8000-000000000001", "mrp-ready-check-0001");
		assertThat(idempotentRuns).isEqualTo(1);
	}

	@Test
	@Transactional
	void createsProductionRecommendationAndExplodesItsEffectiveBom() throws Exception {
		LocalDate today = LocalDate.now();
		jdbcTemplate.update("update planning.independent_demands set quantity = 30, required_date = ? where id = cast(? as uuid)",
				today.plusDays(7), "53000000-0000-4000-8000-000000000001");
		mockMvc.perform(post("/api/v1/planning/mrp-runs").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "mrp-netting-0001").contentType(MediaType.APPLICATION_JSON)
				.content(createBody("净需求与 BOM 展开", today, today.plusDays(45))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.netRequirements[?(@.materialCode == 'GS-800' && @.netQuantity == 18 && @.recommendationType == 'PRODUCTION')]").exists())
				.andExpect(jsonPath("$.netRequirements[?(@.materialCode == 'PM-45' && @.sourceType == 'BOM_COMPONENT' && @.grossQuantity == 18.36)]").exists())
				.andExpect(jsonPath("$.exceptions").isEmpty());
	}

	@Test
	void rejectsInvalidAndEmptyHorizonsWithoutCreatingRun() throws Exception {
		LocalDate today = LocalDate.now();
		mockMvc.perform(post("/api/v1/planning/mrp-runs")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("日期错误", today.plusDays(10), today.plusDays(2))))
				.andExpect(status().isUnprocessableEntity());

		mockMvc.perform(post("/api/v1/planning/mrp-runs")
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("空窗口", today.plusDays(300), today.plusDays(320))))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	@Transactional
	void reportsMissingEffectiveRoutingForMakeMaterial() throws Exception {
		LocalDate today = LocalDate.now();
		jdbcTemplate.update("""
				update planning.independent_demands
				set status = 'ACTIVE', required_date = ?
				where id = cast(? as uuid)
				""", today.plusDays(2), "53000000-0000-4000-8000-000000000002");
		mockMvc.perform(post("/api/v1/planning/mrp-runs")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "mrp-routing-check-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createBody("工艺路线前置检查", today, today.plusDays(45))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.exceptions[?(@.code == 'ROUTING_UNAVAILABLE' && @.materialCode == 'PM-45')]").exists());
	}

	private static String createBody(String name, LocalDate start, LocalDate end) {
		return """
				{"name":"%s","horizonStart":"%s","horizonEnd":"%s"}
				""".formatted(name, start, end);
	}

	private static String extractId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(body);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + body);
		return matcher.group(1);
	}
}
