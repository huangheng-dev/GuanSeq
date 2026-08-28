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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WarehousePutawayIntegrationTest {
  private static final String USERNAME="lin.hao";
  private static final String PASSWORD="guanseq_dev";
  private static final String SOURCE_ID="73000000-0000-4000-8000-000000000011";
  private static final String TARGET_ID="72000000-0000-4000-8000-000000000001";
  private static final ObjectMapper MAPPER=new ObjectMapper();
  private final MockMvc mockMvc;
  private final JdbcTemplate jdbcTemplate;

  WarehousePutawayIntegrationTest(@Autowired WebApplicationContext context,@Autowired JdbcTemplate jdbcTemplate){
    this.mockMvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
    this.jdbcTemplate=jdbcTemplate;
  }

  @Test
  @Transactional
  void createsCompletesIdempotentlyAndReversesWithCompensatingMovements() throws Exception {
    JsonNode refs=MAPPER.readTree(mockMvc.perform(get("/api/v1/warehouse/putaway-reference-data").with(httpBasic(USERNAME,PASSWORD)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.sourceBalances[?(@.id == '"+SOURCE_ID+"')]").exists())
        .andExpect(jsonPath("$.targetLocations[?(@.scanCode == 'LOC:A-01-03')]").exists()).andReturn().getResponse().getContentAsString());
    JsonNode source=findById(refs.path("sourceBalances"),SOURCE_ID);
    String createBody="{\"sourceBalanceId\":\"%s\",\"targetLocationId\":\"%s\",\"quantity\":4,\"expectedSourceBalanceVersion\":%d}"
        .formatted(SOURCE_ID,TARGET_ID,source.path("version").asLong());
    MvcResult createdResult=mockMvc.perform(post("/api/v1/warehouse/putaway-tasks").with(httpBasic(USERNAME,PASSWORD))
        .header("X-Request-Id","putaway-create-it-001").contentType(MediaType.APPLICATION_JSON).content(createBody))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN")).andExpect(jsonPath("$.quantity").value(4)).andReturn();
    JsonNode created=MAPPER.readTree(createdResult.getResponse().getContentAsString());
    String taskId=created.path("id").asText();

    String completeBody="{\"expectedVersion\":%d,\"expectedSourceBalanceVersion\":%d}"
        .formatted(created.path("version").asLong(),created.path("sourceBalanceVersion").asLong());
    MvcResult completedResult=mockMvc.perform(post("/api/v1/warehouse/putaway-tasks/"+taskId+"/complete").with(httpBasic(USERNAME,PASSWORD))
        .header("X-Request-Id","putaway-complete-it-001").contentType(MediaType.APPLICATION_JSON).content(completeBody))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.sourceOutMovementNumber").isNotEmpty()).andExpect(jsonPath("$.targetInMovementNumber").isNotEmpty()).andReturn();
    JsonNode completed=MAPPER.readTree(completedResult.getResponse().getContentAsString());
    assertThat(jdbcTemplate.queryForObject("select on_hand_quantity from warehouse.stock_balances where id=cast(? as uuid)",java.math.BigDecimal.class,SOURCE_ID))
        .isEqualByComparingTo("8");
    assertThat(jdbcTemplate.queryForObject("select on_hand_quantity from warehouse.stock_balances where id=cast(? as uuid)",java.math.BigDecimal.class,completed.path("targetBalanceId").asText()))
        .isEqualByComparingTo("4");
    assertThat(jdbcTemplate.queryForObject("select count(*) from warehouse.stock_movements where source_type='PUTAWAY_TASK' and source_id=cast(? as uuid)",Integer.class,taskId)).isEqualTo(2);

    mockMvc.perform(post("/api/v1/warehouse/putaway-tasks/"+taskId+"/complete").with(httpBasic(USERNAME,PASSWORD))
        .header("X-Request-Id","putaway-complete-it-001").contentType(MediaType.APPLICATION_JSON).content(completeBody))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(taskId));
    assertThat(jdbcTemplate.queryForObject("select count(*) from warehouse.stock_movements where source_type='PUTAWAY_TASK' and source_id=cast(? as uuid)",Integer.class,taskId)).isEqualTo(2);

    String reverseBody="{\"expectedVersion\":%d,\"expectedTargetBalanceVersion\":%d,\"reason\":\"目标库位扫描错误，执行冲回\"}"
        .formatted(completed.path("version").asLong(),completed.path("targetBalanceVersion").asLong());
    mockMvc.perform(post("/api/v1/warehouse/putaway-tasks/"+taskId+"/reverse").with(httpBasic(USERNAME,PASSWORD))
        .header("X-Request-Id","putaway-reverse-it-001").contentType(MediaType.APPLICATION_JSON).content(reverseBody))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVERSED"))
        .andExpect(jsonPath("$.reverseOutMovementNumber").isNotEmpty()).andExpect(jsonPath("$.reverseInMovementNumber").isNotEmpty());
    assertThat(jdbcTemplate.queryForObject("select on_hand_quantity from warehouse.stock_balances where id=cast(? as uuid)",java.math.BigDecimal.class,SOURCE_ID))
        .isEqualByComparingTo("12");
    assertThat(jdbcTemplate.queryForObject("select count(*) from warehouse.stock_movements where source_type='PUTAWAY_TASK' and source_id=cast(? as uuid)",Integer.class,taskId)).isEqualTo(4);
  }

  @Test
  @Transactional
  void reservesOpenQuantityCancelsAndRejectsStaleOrUnauthorizedRequests() throws Exception {
    String createBody="{\"sourceBalanceId\":\"%s\",\"targetLocationId\":\"%s\",\"quantity\":10,\"expectedSourceBalanceVersion\":0}".formatted(SOURCE_ID,TARGET_ID);
    JsonNode task=MAPPER.readTree(mockMvc.perform(post("/api/v1/warehouse/putaway-tasks").with(httpBasic(USERNAME,PASSWORD))
        .header("X-Request-Id","putaway-reserve-it-001").contentType(MediaType.APPLICATION_JSON).content(createBody))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    mockMvc.perform(post("/api/v1/warehouse/putaway-tasks").with(httpBasic(USERNAME,PASSWORD))
        .header("X-Request-Id","putaway-overreserve-it-001").contentType(MediaType.APPLICATION_JSON)
        .content("{\"sourceBalanceId\":\"%s\",\"targetLocationId\":\"%s\",\"quantity\":3,\"expectedSourceBalanceVersion\":0}".formatted(SOURCE_ID,TARGET_ID)))
        .andExpect(status().isUnprocessableEntity());
    mockMvc.perform(post("/api/v1/warehouse/putaway-tasks/"+task.path("id").asText()+"/cancel").with(httpBasic(USERNAME,PASSWORD))
        .header("X-Request-Id","putaway-cancel-it-001").contentType(MediaType.APPLICATION_JSON)
        .content("{\"expectedVersion\":%d,\"reason\":\"现场作业计划取消\"}".formatted(task.path("version").asLong())))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    mockMvc.perform(post("/api/v1/warehouse/putaway-tasks").with(httpBasic(USERNAME,PASSWORD))
        .header("X-Request-Id","putaway-stale-it-001").contentType(MediaType.APPLICATION_JSON)
        .content("{\"sourceBalanceId\":\"%s\",\"targetLocationId\":\"%s\",\"quantity\":1,\"expectedSourceBalanceVersion\":99}".formatted(SOURCE_ID,TARGET_ID)))
        .andExpect(status().isConflict());

    jdbcTemplate.update("update identity.workspace_memberships set role_code='FINANCE_MANAGER' where id=cast(? as uuid)","30000000-0000-4000-8000-000000000101");
    mockMvc.perform(get("/api/v1/warehouse/putaway-reference-data").with(httpBasic(USERNAME,PASSWORD))).andExpect(status().isForbidden());
  }

  private static JsonNode findById(JsonNode items,String id){for(JsonNode item:items)if(id.equals(item.path("id").asText()))return item;throw new AssertionError("not found "+id);}
}
