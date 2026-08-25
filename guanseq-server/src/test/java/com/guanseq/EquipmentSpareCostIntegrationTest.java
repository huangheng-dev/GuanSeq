package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class EquipmentSpareCostIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String SPARE_ID = "c1000000-0000-4000-8000-000000000001";
	private static final String WAREHOUSE_ID = "71000000-0000-4000-8000-000000000001";
	private static final String LOCATION_ID = "72000000-0000-4000-8000-000000000001";
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	EquipmentSpareCostIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void exposesWarehouseAvailabilityAndFinanceCostWithoutCopyingTheirFacts() throws Exception {
		mockMvc.perform(get("/api/v1/equipment/spare-parts")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/equipment/spare-parts?page=0&size=20").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].materialCode").value("BR-6204"))
				.andExpect(jsonPath("$.items[0].preferredWarehouseCode").value("WH-RM"))
				.andExpect(jsonPath("$.items[0].standardUnitCost").value(80.0))
				.andExpect(jsonPath("$.items[0].availableQuantity").isNumber())
				.andExpect(jsonPath("$.items[0].costStatus").value("READY"));
		mockMvc.perform(get("/api/v1/equipment/spare-parts/references").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.materials[?(@.code == 'BR-6204')]").exists())
				.andExpect(jsonPath("$.warehouses[?(@.code == 'WH-RM')]").exists());
	}

	@Test
	@Transactional
	void closesSpareIssueReturnAndLaborReversalAsAuditableRepairCost() throws Exception {
		JsonNode asset = createAsset("EQ-COST-FLOW-001");
		JsonNode broken = assetAction(asset.path("id").asText(), "REPORT_BREAKDOWN", "测试台轴承异常并停止运行", 0,
				"equipment-cost-breakdown-001", 200);
		String repairId = broken.path("events").get(0).path("details").path("repairWorkOrderId").asText();
		JsonNode repair = workOrderAction(repairId, "START", "维修人员确认安全措施后开始拆检", 0, 1,
				"equipment-cost-start-001", 200);
		assertThat(repair.path("status").asText()).isEqualTo("IN_PROGRESS");

		BigDecimalSnapshot before = stockSnapshot();
		JsonNode issued = postCost(repairId, "spare-issues", "equipment-cost-issue-001", Map.of(
				"sparePartId", SPARE_ID, "warehouseId", WAREHOUSE_ID, "quantity", 5,
				"reason", "更换故障轴承并保留领用证据", "expectedVersion", 1), 200);
		assertThat(issued.path("workOrderVersion").asLong()).isEqualTo(2);
		assertThat(issued.path("costEvidence").path("spareCost").decimalValue()).isEqualByComparingTo("400.00");
		String issueId = issued.path("costEvidence").path("spareTransactions").get(0).path("id").asText();
		assertThat(stockSnapshot().onHand()).isEqualByComparingTo(before.onHand().subtract(java.math.BigDecimal.valueOf(5)));

		JsonNode replay = postCost(repairId, "spare-issues", "equipment-cost-issue-001", Map.of(
				"sparePartId", SPARE_ID, "warehouseId", WAREHOUSE_ID, "quantity", 5,
				"reason", "更换故障轴承并保留领用证据", "expectedVersion", 1), 200);
		assertThat(replay.path("workOrderVersion").asLong()).isEqualTo(2);
		assertThat(stockSnapshot().onHand()).isEqualByComparingTo(before.onHand().subtract(java.math.BigDecimal.valueOf(5)));

		JsonNode labor = postCost(repairId, "labor-entries", "equipment-cost-labor-001", Map.of(
				"technicianName", "顾宁", "hours", 2, "hourlyRate", 100, "currency", "CNY",
				"reason", "拆检更换并完成负载验证", "expectedVersion", 2), 200);
		assertThat(labor.path("costEvidence").path("totalCost").decimalValue()).isEqualByComparingTo("600.00");
		String laborId = labor.path("costEvidence").path("laborTransactions").get(0).path("id").asText();

		JsonNode returned = postCost(repairId, "spare-returns", "equipment-cost-return-001", Map.of(
				"issueTransactionId", issueId, "locationId", LOCATION_ID, "quantity", 2,
				"reason", "未使用备件退回原材料仓", "expectedVersion", 3), 200);
		assertThat(returned.path("costEvidence").path("spareCost").decimalValue()).isEqualByComparingTo("240.00");
		assertThat(returned.path("costEvidence").path("totalCost").decimalValue()).isEqualByComparingTo("440.00");

		postCost(repairId, "spare-returns", "equipment-cost-over-return", Map.of(
				"issueTransactionId", issueId, "locationId", LOCATION_ID, "quantity", 4,
				"reason", "验证累计退回数量边界", "expectedVersion", 4), 422);
		JsonNode reversed = postLaborReversal(repairId, laborId, "equipment-cost-labor-reversal-001",
				Map.of("reason", "人工费率录入错误，保留冲销证据", "expectedVersion", 4), 200);
		assertThat(reversed.path("workOrderVersion").asLong()).isEqualTo(5);
		assertThat(reversed.path("costEvidence").path("laborCost").decimalValue()).isEqualByComparingTo("0.00");
		assertThat(reversed.path("costEvidence").path("totalCost").decimalValue()).isEqualByComparingTo("240.00");
		mockMvc.perform(get("/api/v1/equipment/work-orders/{id}", repairId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.costEvidence.spareTransactions.length()").value(2))
				.andExpect(jsonPath("$.costEvidence.laborTransactions.length()").value(2))
				.andExpect(jsonPath("$.costEvidence.basis").value(org.hamcrest.Matchers.containsString("不生成财务凭证")));
	}

	@Test
	@Transactional
	void deniesCostWritesWithoutMaintenanceRole() throws Exception {
		jdbcTemplate.update("update identity.workspace_memberships set role_code = 'QUALITY_INSPECTOR' where user_id = cast(? as uuid) and workspace_id = cast(? as uuid)",
				"20000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000101");
		mockMvc.perform(post("/api/v1/equipment/spare-parts").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "equipment-spare-denied").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"materialId":"42000000-0000-4000-8000-000000000004",
						 "preferredWarehouseId":"71000000-0000-4000-8000-000000000001",
						 "reorderPoint":10,"reason":"验证无权限用户不能建档"}
						"""))
				.andExpect(status().isForbidden());
	}

	private JsonNode createAsset(String code) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/assets").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "equipment-cost-create-asset-001").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"assetCode":"%s","assetName":"成本闭环测试台","category":"QUALITY",
						 "location":"测试区 COST-01","responsiblePerson":"顾宁","reason":"建立维修成本闭环测试设备"}
						""".formatted(code))).andExpect(status().isCreated()).andReturn();
		return json(result);
	}

	private JsonNode assetAction(String id, String action, String reason, long version, String requestId, int code) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/assets/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
				.content(MAPPER.writeValueAsString(Map.of("action", action, "reason", reason, "expectedVersion", version))))
				.andExpect(status().is(code)).andReturn();
		return code == 200 ? json(result) : MAPPER.createObjectNode();
	}

	private JsonNode workOrderAction(String id, String action, String reason, long version, long assetVersion,
			String requestId, int code) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>(); body.put("action", action); body.put("reason", reason);
		body.put("expectedVersion", version); body.put("assetExpectedVersion", assetVersion);
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/work-orders/{id}/actions", id).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
				.content(MAPPER.writeValueAsString(body))).andExpect(status().is(code)).andReturn();
		return code == 200 ? json(result) : MAPPER.createObjectNode();
	}

	private JsonNode postCost(String id, String action, String requestId, Map<String, Object> body, int code) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/work-orders/{id}/{action}", id, action)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
				.content(MAPPER.writeValueAsString(body))).andExpect(status().is(code)).andReturn();
		return code == 200 ? json(result) : MAPPER.createObjectNode();
	}

	private JsonNode postLaborReversal(String id, String entryId, String requestId, Map<String, Object> body, int code) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/equipment/work-orders/{id}/labor-entries/{entryId}/reversals", id, entryId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_JSON)
				.content(MAPPER.writeValueAsString(body))).andExpect(status().is(code)).andReturn();
		return code == 200 ? json(result) : MAPPER.createObjectNode();
	}

	private BigDecimalSnapshot stockSnapshot() {
		return new BigDecimalSnapshot(jdbcTemplate.queryForObject(
				"select on_hand_quantity from warehouse.stock_balances where id = cast(? as uuid)", java.math.BigDecimal.class,
				"73000000-0000-4000-8000-000000000003"));
	}

	private JsonNode json(MvcResult result) throws Exception { return MAPPER.readTree(result.getResponse().getContentAsString()); }
	private record BigDecimalSnapshot(java.math.BigDecimal onHand) { }
}
