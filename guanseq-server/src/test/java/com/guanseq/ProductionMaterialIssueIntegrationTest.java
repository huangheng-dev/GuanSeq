package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductionMaterialIssueIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String ORDER_ID = "91000000-0000-4000-8000-000000000001";
	private static final String RAW_WAREHOUSE = "71000000-0000-4000-8000-000000000001";
	private static final String RAW_LOCATION = "72000000-0000-4000-8000-000000000001";
	private static final String PM_MATERIAL = "42000000-0000-4000-8000-000000000002";
	private static final String TENANT = "00000000-0000-4000-8000-000000000001";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	ProductionMaterialIssueIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@BeforeEach
	void resetFinanceSampleIssue() {
		String issueId = "95000000-0000-4000-8000-000000000001";
		jdbcTemplate.update("delete from production.material_stock_transactions where issue_id = cast(? as uuid)", issueId);
		jdbcTemplate.update("delete from warehouse.stock_movements where id in (cast(? as uuid), cast(? as uuid))",
				"74000000-0000-4000-8000-000000000101", "74000000-0000-4000-8000-000000000102");
		jdbcTemplate.update("delete from production.material_issue_lines where issue_id = cast(? as uuid)", issueId);
		jdbcTemplate.update("delete from production.material_issues where id = cast(? as uuid)", issueId);
		jdbcTemplate.update("delete from warehouse.stock_balances where id = cast(? as uuid)", "73000000-0000-4000-8000-000000000010");
		jdbcTemplate.update("update warehouse.stock_balances set on_hand_quantity = 500 where id = cast(? as uuid)",
				"73000000-0000-4000-8000-000000000003");
	}

	@Test
	@Transactional
	void generatesIssuesReturnsAndCreatesWarehouseMovements() throws Exception {
		insertRawComponentStock();
		mockMvc.perform(get("/api/v1/production/material-issue-reference-data").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productionOrders[?(@.orderNumber == 'MO-260815-012')]").exists())
				.andExpect(jsonPath("$.warehouses[?(@.code == 'WH-RM')]").exists());

		MvcResult created = mockMvc.perform(post("/api/v1/production/material-issues").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "material-issue-test-0001").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"productionOrderId":"%s","warehouseId":"%s"}
						""".formatted(ORDER_ID, RAW_WAREHOUSE)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.lines.length()").value(2)).andReturn();
		JsonNode issue = MAPPER.readTree(created.getResponse().getContentAsString());
		String issueId = issue.path("id").asText();
		JsonNode pmLine = findLine(issue, "PM-45");
		JsonNode brLine = findLine(issue, "BR-6204");
		assertThat(pmLine.path("requiredQuantity").decimalValue()).isEqualByComparingTo("8.16");
		assertThat(brLine.path("requiredQuantity").decimalValue()).isEqualByComparingTo("32.32");

		mockMvc.perform(post("/api/v1/production/material-issues/{id}/actions", issueId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "material-issue-test-0002").contentType(MediaType.APPLICATION_JSON)
				.content(actionBody(0, pmLine.path("id").asText(), 1, pmLine.path("version").asLong(),
						brLine.path("id").asText(), 4, brLine.path("version").asLong())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIAL"))
				.andExpect(jsonPath("$.stockTransactions.length()").value(2));

		mockMvc.perform(post("/api/v1/production/material-issues/{id}/actions", issueId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "material-issue-test-0002").contentType(MediaType.APPLICATION_JSON)
				.content(actionBody(0, pmLine.path("id").asText(), 1, pmLine.path("version").asLong(),
						brLine.path("id").asText(), 4, brLine.path("version").asLong())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIAL"));

		mockMvc.perform(post("/api/v1/production/material-issues/{id}/returns", issueId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "material-issue-test-0003").contentType(MediaType.APPLICATION_JSON)
				.content(returnBody(pmLine.path("id").asText(), 1, pmLine.path("version").asLong() + 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.returns.length()").value(1));

		mockMvc.perform(post("/api/v1/production/material-issues/{id}/returns", issueId).with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "material-issue-test-0003").contentType(MediaType.APPLICATION_JSON)
				.content(returnBody(pmLine.path("id").asText(), 1, pmLine.path("version").asLong() + 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.returns.length()").value(1));

		JsonNode current = MAPPER.readTree(mockMvc.perform(get("/api/v1/production/material-issues/{id}", issueId)
				.with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		JsonNode currentPm = findLine(current, "PM-45");
		JsonNode currentBr = findLine(current, "BR-6204");
		mockMvc.perform(post("/api/v1/production/material-issues/{id}/actions", issueId).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionBody(1, currentPm.path("id").asText(), currentPm.path("issuableQuantity").decimalValue(), currentPm.path("version").asLong(),
						currentBr.path("id").asText(), currentBr.path("issuableQuantity").decimalValue(), currentBr.path("version").asLong())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISSUED"));

		Integer issueMovements = jdbcTemplate.queryForObject("""
				select count(*) from warehouse.stock_movements m
				join production.material_stock_transactions t on t.movement_id = m.id
				where t.issue_id = cast(? as uuid) and m.movement_type = 'ISSUE'
				""", Integer.class, issueId);
		Integer returnMovements = jdbcTemplate.queryForObject("""
				select count(*) from warehouse.stock_movements m
				join production.material_stock_transactions t on t.movement_id = m.id
				where t.issue_id = cast(? as uuid) and m.movement_type = 'RETURN'
				""", Integer.class, issueId);
		BigDecimal pmOnHand = jdbcTemplate.queryForObject("""
				select on_hand_quantity from warehouse.stock_balances
				where tenant_organization_id = cast(? as uuid) and warehouse_id = cast(? as uuid)
				and material_id = cast(? as uuid) and lot_number = 'LOT-PM-TEST'
				""", BigDecimal.class, TENANT, RAW_WAREHOUSE, PM_MATERIAL);
		assertThat(issueMovements).isEqualTo(5);
		assertThat(returnMovements).isEqualTo(1);
		assertThat(pmOnHand).isEqualByComparingTo("2.84");
	}

	@Test
	@Transactional
	void rejectsIssueWhenComponentStockIsUnavailable() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/production/material-issues").with(httpBasic(USERNAME, PASSWORD))
				.header("X-Request-Id", "material-issue-shortage-0001").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"productionOrderId":"%s","warehouseId":"%s"}
						""".formatted(ORDER_ID, RAW_WAREHOUSE)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
		String issueId = MAPPER.readTree(created.getResponse().getContentAsString()).path("id").asText();
		JsonNode issue = MAPPER.readTree(mockMvc.perform(get("/api/v1/production/material-issues/{id}", issueId).with(httpBasic(USERNAME, PASSWORD)))
				.andReturn().getResponse().getContentAsString());
		JsonNode pm = findLine(issue, "PM-45");
		mockMvc.perform(post("/api/v1/production/material-issues/{id}/actions", issueId).with(httpBasic(USERNAME, PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionBody(0, pm.path("id").asText(), pm.path("requiredQuantity").decimalValue(), pm.path("version").asLong())))
				.andExpect(status().isUnprocessableEntity());
	}

	private void insertRawComponentStock() {
		jdbcTemplate.update("""
				insert into warehouse.stock_balances (
				    id, tenant_organization_id, owning_organization_id, workspace_id,
				    warehouse_id, warehouse_code, warehouse_name, location_id, location_code, location_name,
				    material_id, material_code, material_name, material_specification, unit, lot_number, quality_status,
				    on_hand_quantity, allocated_quantity, frozen_quantity, updated_by, updated_at)
				values (cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid),
				    cast(? as uuid), 'WH-RM', '原材料仓', cast(? as uuid), 'A-01-03', '原料 A 区 01 排 03 位',
				    cast(? as uuid), 'PM-45', '精密传动模组', '45mm 高精度', '套', 'LOT-PM-TEST', 'AVAILABLE',
				    10, 0, 0, cast(? as uuid), current_timestamp)
				on conflict (tenant_organization_id, warehouse_id, location_id, material_id, lot_number, quality_status) do nothing
				""", UUID.randomUUID(), TENANT, "00000000-0000-4000-8000-000000000101", "10000000-0000-4000-8000-000000000101",
				RAW_WAREHOUSE, RAW_LOCATION, PM_MATERIAL, "20000000-0000-4000-8000-000000000001");
	}

	private static JsonNode findLine(JsonNode issue, String materialCode) {
		for (JsonNode line : issue.path("lines")) if (materialCode.equals(line.path("componentMaterialCode").asText())) return line;
		throw new IllegalArgumentException("Missing line " + materialCode);
	}

	private static String actionBody(long issueVersion, String line1Id, Number quantity1, long version1, String line2Id, Number quantity2, long version2) {
		return """
				{"action":"ISSUE","expectedVersion":%d,"comment":"按生产进度发料","lines":[
				  {"lineId":"%s","quantity":%s,"expectedLineVersion":%d},
				  {"lineId":"%s","quantity":%s,"expectedLineVersion":%d}]}
				""".formatted(issueVersion, line1Id, quantity1, version1, line2Id, quantity2, version2);
	}

	private static String actionBody(long issueVersion, String lineId, Number quantity, long version) {
		return """
				{"action":"ISSUE","expectedVersion":%d,"comment":"库存不足发料","lines":[
				  {"lineId":"%s","quantity":%s,"expectedLineVersion":%d}]}
				""".formatted(issueVersion, lineId, quantity, version);
	}

	private static String returnBody(String lineId, Number quantity, long version) {
		return """
				{"locationId":"%s","reason":"未使用组件退回原材料仓","lines":[
				  {"lineId":"%s","quantity":%s,"expectedLineVersion":%d,"reason":"剩余组件退回"}]}
				""".formatted(RAW_LOCATION, lineId, quantity, version);
	}
}







