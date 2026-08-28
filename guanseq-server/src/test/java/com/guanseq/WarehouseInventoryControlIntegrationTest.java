package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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

@Testcontainers(disabledWithoutDocker=true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WarehouseInventoryControlIntegrationTest {
    private static final String USERNAME="lin.hao";
    private static final String PASSWORD="guanseq_dev";
    private static final String SOURCE_ID="73000000-0000-4000-8000-000000000003";
    private static final String TARGET_LOCATION_ID="72000000-0000-4000-8000-000000000012";
    private static final ObjectMapper MAPPER=new ObjectMapper();
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    WarehouseInventoryControlIntegrationTest(@Autowired WebApplicationContext context,@Autowired JdbcTemplate jdbcTemplate){
        this.mockMvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
        this.jdbcTemplate=jdbcTemplate;
    }

    @Test
    @Transactional
    void transfersIdempotentlyAndReversesWithFourImmutableMovements() throws Exception {
        JsonNode source=referenceBalance();
        BigDecimal original=source.path("onHandQuantity").decimalValue();
        String create="{\"sourceBalanceId\":\"%s\",\"targetLocationId\":\"%s\",\"quantity\":10,\"expectedSourceBalanceVersion\":%d,\"reason\":\"原料库位整理调拨\"}"
                .formatted(SOURCE_ID,TARGET_LOCATION_ID,source.path("version").asLong());
        JsonNode task=json(postJson("/api/v1/warehouse/transfer-tasks","transfer-create-it-001",create)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN")).andExpect(jsonPath("$.quantity").value(10)).andReturn());
        String complete="{\"expectedVersion\":%d,\"expectedSourceBalanceVersion\":%d}"
                .formatted(task.path("version").asLong(),task.path("sourceBalanceVersion").asLong());
        JsonNode completed=json(postJson("/api/v1/warehouse/transfer-tasks/"+task.path("id").asText()+"/complete","transfer-complete-it-001",complete)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sourceOutMovementNumber").isNotEmpty()).andExpect(jsonPath("$.targetInMovementNumber").isNotEmpty()).andReturn());
        assertQuantity(SOURCE_ID,original.subtract(BigDecimal.TEN).toPlainString());assertQuantity(completed.path("targetBalanceId").asText(),"10");
        assertThat(movementCount("TRANSFER_TASK",task.path("id").asText())).isEqualTo(2);
        postJson("/api/v1/warehouse/transfer-tasks/"+task.path("id").asText()+"/complete","transfer-complete-it-001",complete)
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(task.path("id").asText()));
        assertThat(movementCount("TRANSFER_TASK",task.path("id").asText())).isEqualTo(2);
        String reverse="{\"expectedVersion\":%d,\"expectedTargetBalanceVersion\":%d,\"reason\":\"调拨目标选择错误\"}"
                .formatted(completed.path("version").asLong(),completed.path("targetBalanceVersion").asLong());
        postJson("/api/v1/warehouse/transfer-tasks/"+task.path("id").asText()+"/reverse","transfer-reverse-it-001",reverse)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVERSED"));
        assertQuantity(SOURCE_ID,original.toPlainString());assertQuantity(completed.path("targetBalanceId").asText(),"0");
        assertThat(movementCount("TRANSFER_TASK",task.path("id").asText())).isEqualTo(4);
    }

    @Test
    @Transactional
    void recordsApprovesAndReversesCountDifferenceWithoutOverwritingBalance() throws Exception {
        JsonNode source=referenceBalance();
        BigDecimal original=source.path("onHandQuantity").decimalValue();
        BigDecimal countedQuantity=original.subtract(new BigDecimal("3"));
        JsonNode task=json(postJson("/api/v1/warehouse/stock-count-tasks","count-create-it-001",
                "{\"balanceId\":\"%s\",\"expectedBalanceVersion\":%d}".formatted(SOURCE_ID,source.path("version").asLong()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN")).andExpect(jsonPath("$.bookOnHand").value(original.doubleValue())).andReturn());
        JsonNode counted=json(postJson("/api/v1/warehouse/stock-count-tasks/"+task.path("id").asText()+"/record-count","count-record-it-001",
                "{\"expectedVersion\":%d,\"expectedBalanceVersion\":%d,\"countedQuantity\":%s,\"note\":\"现场逐箱复核完成\"}"
                        .formatted(task.path("version").asLong(),task.path("currentBalanceVersion").asLong(),countedQuantity.toPlainString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COUNTED")).andExpect(jsonPath("$.differenceQuantity").value(-3)).andReturn());
        assertQuantity(SOURCE_ID,original.toPlainString());
        JsonNode approved=json(postJson("/api/v1/warehouse/stock-count-tasks/"+task.path("id").asText()+"/approve","count-approve-it-001",
                "{\"expectedVersion\":%d,\"expectedBalanceVersion\":%d,\"comment\":\"批准盘亏三件调整\"}"
                        .formatted(counted.path("version").asLong(),counted.path("currentBalanceVersion").asLong()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.adjustmentMovementType").value("ISSUE")).andReturn());
        assertQuantity(SOURCE_ID,countedQuantity.toPlainString());assertThat(movementCount("STOCK_COUNT_TASK",task.path("id").asText())).isEqualTo(1);
        postJson("/api/v1/warehouse/stock-count-tasks/"+task.path("id").asText()+"/approve","count-approve-it-001",
                "{\"expectedVersion\":%d,\"expectedBalanceVersion\":%d,\"comment\":\"批准盘亏三件调整\"}"
                        .formatted(counted.path("version").asLong(),counted.path("currentBalanceVersion").asLong()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(task.path("id").asText()));
        assertThat(movementCount("STOCK_COUNT_TASK",task.path("id").asText())).isEqualTo(1);
        postJson("/api/v1/warehouse/stock-count-tasks/"+task.path("id").asText()+"/reverse","count-reverse-it-001",
                "{\"expectedVersion\":%d,\"expectedBalanceVersion\":%d,\"reason\":\"复盘确认账实相符\"}"
                        .formatted(approved.path("version").asLong(),approved.path("currentBalanceVersion").asLong()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVERSED")).andExpect(jsonPath("$.reverseMovementType").value("RECEIPT"));
        assertQuantity(SOURCE_ID,original.toPlainString());assertThat(movementCount("STOCK_COUNT_TASK",task.path("id").asText())).isEqualTo(2);
    }

    @Test
    @Transactional
    void rejectsOverReservationActiveCountStaleVersionAndUnauthorizedAccess() throws Exception {
        JsonNode source=referenceBalance();
        JsonNode transfer=json(postJson("/api/v1/warehouse/transfer-tasks","transfer-reserve-it-001",
                "{\"sourceBalanceId\":\"%s\",\"targetLocationId\":\"%s\",\"quantity\":400,\"expectedSourceBalanceVersion\":%d,\"reason\":\"批量库位整理调拨\"}"
                        .formatted(SOURCE_ID,TARGET_LOCATION_ID,source.path("version").asLong()))
                .andExpect(status().isOk()).andReturn());
        postJson("/api/v1/warehouse/transfer-tasks","transfer-overreserve-it-001",
                "{\"sourceBalanceId\":\"%s\",\"targetLocationId\":\"%s\",\"quantity\":20,\"expectedSourceBalanceVersion\":%d,\"reason\":\"再次批量整理调拨\"}"
                        .formatted(SOURCE_ID,TARGET_LOCATION_ID,source.path("version").asLong())).andExpect(status().isUnprocessableEntity());
        postJson("/api/v1/warehouse/stock-count-tasks","count-blocked-it-001",
                "{\"balanceId\":\"%s\",\"expectedBalanceVersion\":%d}".formatted(SOURCE_ID,source.path("version").asLong())).andExpect(status().isConflict());
        postJson("/api/v1/warehouse/transfer-tasks/"+transfer.path("id").asText()+"/cancel","transfer-cancel-it-001",
                "{\"expectedVersion\":%d,\"reason\":\"暂停本次库位整理\"}".formatted(transfer.path("version").asLong())).andExpect(status().isOk());
        postJson("/api/v1/warehouse/stock-count-tasks","count-stale-it-001",
                "{\"balanceId\":\"%s\",\"expectedBalanceVersion\":99}".formatted(SOURCE_ID)).andExpect(status().isConflict());
        jdbcTemplate.update("update identity.workspace_memberships set role_code='FINANCE_MANAGER' where id=cast(? as uuid)","30000000-0000-4000-8000-000000000101");
        mockMvc.perform(get("/api/v1/warehouse/inventory-control-reference-data").with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isForbidden());
    }

    private JsonNode referenceBalance() throws Exception {
        MvcResult result=mockMvc.perform(get("/api/v1/warehouse/inventory-control-reference-data").with(httpBasic(USERNAME,PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balances[?(@.id == '"+SOURCE_ID+"')]").exists())
                .andExpect(jsonPath("$.targetLocations[?(@.scanCode == 'LOC:A-01-04')]").exists()).andReturn();
        JsonNode refs=MAPPER.readTree(result.getResponse().getContentAsString());
        for(JsonNode item:refs.path("balances"))if(SOURCE_ID.equals(item.path("id").asText()))return item;
        throw new AssertionError("reference balance not found");
    }
    private org.springframework.test.web.servlet.ResultActions postJson(String path,String requestId,String body) throws Exception {
        return mockMvc.perform(post(path).with(httpBasic(USERNAME,PASSWORD)).header("X-Request-Id",requestId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private static JsonNode json(MvcResult result) throws Exception {return MAPPER.readTree(result.getResponse().getContentAsString());}
    private void assertQuantity(String id,String expected){assertThat(jdbcTemplate.queryForObject(
            "select on_hand_quantity from warehouse.stock_balances where id=cast(? as uuid)",BigDecimal.class,id)).isEqualByComparingTo(expected);}
    private int movementCount(String type,String id){return jdbcTemplate.queryForObject(
            "select count(*) from warehouse.stock_movements where source_type=? and source_id=cast(? as uuid)",Integer.class,type,id);}
}
