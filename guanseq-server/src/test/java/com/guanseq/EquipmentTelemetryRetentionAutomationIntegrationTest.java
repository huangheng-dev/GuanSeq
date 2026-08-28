package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"guanseq.telemetry.polling-enabled=false",
		"guanseq.telemetry.retention-scheduler-enabled=true",
		"guanseq.telemetry.retention-dispatch-delay-ms=3600000"
})
class EquipmentTelemetryRetentionAutomationIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String TENANT_ID = "00000000-0000-4000-8000-000000000001";
	private static final String WORKSPACE_ID = "10000000-0000-4000-8000-000000000101";
	private static final String USER_ID = "20000000-0000-4000-8000-000000000001";
	private static final String CONNECTION_ID = "f1000000-0000-4000-8000-000000000901";
	private static final String POINT_ID = "f2000000-0000-4000-8000-000000000901";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	EquipmentTelemetryRetentionAutomationIntegrationTest(@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void enablesBoundedAutomationHonorsLeaseAndAcknowledgesFailureResponsibility() throws Exception {
		seedConnectionAndExpiredSamples(10_001);

		mockMvc.perform(put("/api/v1/equipment/telemetry-retention-policy")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "retention-auto-policy-001")
					.contentType(MediaType.APPLICATION_JSON).content("""
							{"retentionDays":7,"expectedVersion":0,"reason":"启用受控自动样本清理",
							 "automaticCleanupEnabled":true,"cleanupIntervalHours":1}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.schedulerAvailable").value(true))
				.andExpect(jsonPath("$.automaticCleanupEnabled").value(true))
				.andExpect(jsonPath("$.cleanupIntervalHours").value(1))
				.andExpect(jsonPath("$.nextCleanupAt").isNotEmpty())
				.andExpect(jsonPath("$.version").value(0));

		String firstBody = "{\"expectedVersion\":0,\"reason\":\"立即验证一万条批次上限\"}";
		mockMvc.perform(post(
					"/api/v1/equipment/telemetry-retention-policy/automation/run-now")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "retention-auto-run-001")
					.contentType(MediaType.APPLICATION_JSON).content(firstBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.run.status").value("PARTIAL"))
				.andExpect(jsonPath("$.run.deletedSampleCount").value(10_000))
				.andExpect(jsonPath("$.run.remainingExpiredCount").value(1))
				.andExpect(jsonPath("$.run.attentionStatus").value("NONE"))
				.andExpect(jsonPath("$.policy.version").value(1))
				.andExpect(jsonPath("$.replayed").value(false));
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from equipment.telemetry_samples
				where tenant_organization_id = cast(? as uuid) and workspace_id = cast(? as uuid)
				""", Long.class, TENANT_ID, WORKSPACE_ID)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/equipment/telemetry-retention-policy/automation/run-now")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "retention-auto-run-001")
					.contentType(MediaType.APPLICATION_JSON).content(firstBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.run.status").value("PARTIAL"))
				.andExpect(jsonPath("$.replayed").value(true));

		jdbcTemplate.update("""
				insert into equipment.telemetry_retention_leases
				(tenant_organization_id, workspace_id, owner_id, acquired_at, lease_until)
				values (cast(? as uuid), cast(? as uuid), 'other-instance', now(), now() + interval '5 minutes')
				""", TENANT_ID, WORKSPACE_ID);
		String secondBody = "{\"expectedVersion\":1,\"reason\":\"租约释放后继续清理剩余样本\"}";
		mockMvc.perform(post("/api/v1/equipment/telemetry-retention-policy/automation/run-now")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "retention-auto-run-002")
					.contentType(MediaType.APPLICATION_JSON).content(secondBody))
				.andExpect(status().isConflict());
		jdbcTemplate.update("delete from equipment.telemetry_retention_leases where workspace_id = cast(? as uuid)",
				WORKSPACE_ID);
		mockMvc.perform(post("/api/v1/equipment/telemetry-retention-policy/automation/run-now")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "retention-auto-run-002")
					.contentType(MediaType.APPLICATION_JSON).content(secondBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.run.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.run.deletedSampleCount").value(1))
				.andExpect(jsonPath("$.run.remainingExpiredCount").value(0))
				.andExpect(jsonPath("$.policy.version").value(2));

		jdbcTemplate.update("""
				insert into equipment.telemetry_samples
				(id, tenant_organization_id, workspace_id, asset_id, connection_id, point_id, point_code,
				 raw_value, numeric_value, boolean_value, quality, device_time, received_at, message_version, source_protocol)
				values (cast('f3000000-0000-4000-8000-000000000901' as uuid), cast(? as uuid), cast(? as uuid),
				 cast('a1000000-0000-4000-8000-000000000001' as uuid), cast(? as uuid), cast(? as uuid),
				 'AUTO_POINT', '1', 1, null, 'GOOD', null, now() - interval '8 days', 1, 'MODBUS_TCP')
				""", TENANT_ID, WORKSPACE_ID, CONNECTION_ID, POINT_ID);
		jdbcTemplate.execute("""
				create function equipment.fail_retention_test_delete() returns trigger language plpgsql as $$
				begin raise exception 'controlled retention delete failure'; end $$
				""");
		jdbcTemplate.execute("""
				create trigger fail_retention_test_delete before delete on equipment.telemetry_samples
				for each row execute function equipment.fail_retention_test_delete()
				""");
		mockMvc.perform(post("/api/v1/equipment/telemetry-retention-policy/automation/run-now")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "retention-auto-run-failed-001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"expectedVersion\":2,\"reason\":\"验证删除故障独立留下责任证据\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.run.status").value("FAILED"))
				.andExpect(jsonPath("$.run.failureCode").value("DATABASE_OPERATION_FAILED"))
				.andExpect(jsonPath("$.run.attentionStatus").value("OPEN"))
				.andExpect(jsonPath("$.policy.version").value(3))
				.andExpect(jsonPath("$.policy.consecutiveFailures").value(1));
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from equipment.telemetry_samples
				where tenant_organization_id = cast(? as uuid) and workspace_id = cast(? as uuid)
				""", Long.class, TENANT_ID, WORKSPACE_ID)).isEqualTo(1);
		String failedRunId = jdbcTemplate.queryForObject("""
				select id::text from equipment.telemetry_retention_runs
				where workspace_id = cast(? as uuid) and status = 'FAILED'
				""", String.class, WORKSPACE_ID);
		String acknowledgement = "{\"note\":\"设备经理已确认并跟踪数据库恢复\"}";
		mockMvc.perform(post("/api/v1/equipment/telemetry-retention-runs/{id}/acknowledge", failedRunId)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "retention-auto-ack-001")
					.contentType(MediaType.APPLICATION_JSON).content(acknowledgement))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.run.attentionStatus").value("ACKNOWLEDGED"))
				.andExpect(jsonPath("$.run.acknowledgedBy").value(USER_ID))
				.andExpect(jsonPath("$.run.acknowledgementNote").value("设备经理已确认并跟踪数据库恢复"))
				.andExpect(jsonPath("$.replayed").value(false));
		mockMvc.perform(post("/api/v1/equipment/telemetry-retention-runs/{id}/acknowledge", failedRunId)
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "retention-auto-ack-001")
					.contentType(MediaType.APPLICATION_JSON).content(acknowledgement))
				.andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));

		jdbcTemplate.execute("drop trigger fail_retention_test_delete on equipment.telemetry_samples");
		jdbcTemplate.execute("drop function equipment.fail_retention_test_delete()");
		mockMvc.perform(post("/api/v1/equipment/telemetry-retention-policy/automation/run-now")
					.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "retention-auto-run-recovered-001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"expectedVersion\":3,\"reason\":\"故障解除后人工恢复运行\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.run.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.run.deletedSampleCount").value(1))
				.andExpect(jsonPath("$.policy.version").value(4))
				.andExpect(jsonPath("$.policy.consecutiveFailures").value(0));
	}

	private void seedConnectionAndExpiredSamples(int count) {
		jdbcTemplate.update("""
				insert into equipment.telemetry_connections
				(id, tenant_organization_id, owning_organization_id, workspace_id, asset_id,
				 connection_code, name, protocol, endpoint_type, host, port, unit_id,
				 connect_timeout_ms, read_timeout_ms, poll_interval_seconds, status, version,
				 creation_request_id, created_by, created_at, updated_by, updated_at)
				values (cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid),
				 cast('a1000000-0000-4000-8000-000000000001' as uuid), 'TEL-AUTO-001', '自动保留测试连接',
				 'MODBUS_TCP', 'SIMULATOR', '127.0.0.1', 1502, 1, 500, 500, 60, 'PAUSED', 0,
				 'retention-auto-seed-001', cast(? as uuid), now(), cast(? as uuid), now())
				""", CONNECTION_ID, TENANT_ID, TENANT_ID, WORKSPACE_ID, USER_ID, USER_ID);
		jdbcTemplate.update("""
				insert into equipment.telemetry_points
				(id, connection_id, point_code, name, register_type, address, value_type, scale,
				 value_offset, engineering_unit, valid_min, valid_max, sort_order, created_at)
				values (cast(? as uuid), cast(? as uuid), 'AUTO_POINT', '自动保留测试点位',
				 'HOLDING_REGISTER', 0, 'UINT16', 1, 0, null, 0, 100, 1, now())
				""", POINT_ID, CONNECTION_ID);
		int inserted = jdbcTemplate.update("""
				insert into equipment.telemetry_samples
				(id, tenant_organization_id, workspace_id, asset_id, connection_id, point_id, point_code,
				 raw_value, numeric_value, boolean_value, quality, device_time, received_at, message_version, source_protocol)
				select md5('retention-auto-' || series)::uuid, cast(? as uuid), cast(? as uuid),
				 cast('a1000000-0000-4000-8000-000000000001' as uuid), cast(? as uuid), cast(? as uuid),
				 'AUTO_POINT', series::text, series, null, 'GOOD', null,
				 now() - interval '8 days' - (series * interval '1 millisecond'), 1, 'MODBUS_TCP'
				from generate_series(1, ?) series
				""", TENANT_ID, WORKSPACE_ID, CONNECTION_ID, POINT_ID, count);
		assertThat(inserted).isEqualTo(count);
	}
}
