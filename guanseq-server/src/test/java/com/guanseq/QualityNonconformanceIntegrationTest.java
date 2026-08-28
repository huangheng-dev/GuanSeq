package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import jakarta.persistence.EntityManager;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QualityNonconformanceIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private static final String TENANT = "00000000-0000-4000-8000-000000000001";
	private static final String ORGANIZATION = "00000000-0000-4000-8000-000000000101";
	private static final String USER = "20000000-0000-4000-8000-000000000001";
	private static final String EAST = "10000000-0000-4000-8000-000000000101";
	private static final String SOUTH = "10000000-0000-4000-8000-000000000102";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;
	private final EntityManager entityManager;

	QualityNonconformanceIntegrationTest(@Autowired WebApplicationContext context, @Autowired JdbcTemplate jdbcTemplate,
			@Autowired EntityManager entityManager) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
		this.jdbcTemplate = jdbcTemplate;
		this.entityManager = entityManager;
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/quality/nonconformances")).andExpect(status().isUnauthorized());
	}

	@Test
	@Transactional
	void createsFromRejectedInspectionAndClosesARecoverableCapaLoop() throws Exception {
		String inspectionId = UUID.randomUUID().toString();
		insertPendingInspection(inspectionId, EAST, "FINAL", "PRODUCTION_REPORT", null, null, null);
		mockMvc.perform(post("/api/v1/quality/final-inspections/{id}/complete", inspectionId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", "nc-inspection-complete-1")
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"acceptedQuantity":8,"rejectedQuantity":2,"inspector":"吴倩","defectDescription":"端子扭矩低于下限","conclusion":"八件放行，两件隔离","expectedVersion":0}
					"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.result").value("PARTIALLY_PASSED"));

		MvcResult listed = mockMvc.perform(get("/api/v1/quality/nonconformances")
				.with(httpBasic(USERNAME, PASSWORD)).param("query", "FQI-NC-TEST"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].status").value("OPEN"))
				.andExpect(jsonPath("$.items[0].nonconformingQuantity").value(2))
				.andExpect(jsonPath("$.canReview").value(true)).andReturn();
		String caseId = com.jayway.jsonpath.JsonPath.parse(listed.getResponse().getContentAsString()).read("$.items[0].id");

		act(caseId, "nc-review-invalid-1", """
				{"action":"REVIEW","expectedVersion":0,"severity":"HIGH","immediateContainment":"隔离同批次库存","reviewConclusion":"关键尺寸超差","capaRequired":false}
				""").andExpect(status().isUnprocessableEntity());
		act(caseId, "nc-review-1", """
				{"action":"REVIEW","expectedVersion":0,"severity":"HIGH","immediateContainment":"隔离同批次库存并暂停放行","reviewConclusion":"装配扭矩控制失效，需要纠正措施","capaRequired":true}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));
		act(caseId, "nc-review-1", """
				{"action":"REVIEW","expectedVersion":0,"severity":"HIGH","immediateContainment":"隔离同批次库存并暂停放行","reviewConclusion":"装配扭矩控制失效，需要纠正措施","capaRequired":true}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));

		act(caseId, "nc-dispose-1", """
				{"action":"DISPOSE","expectedVersion":1,"dispositionType":"REWORK","dispositionDecision":"隔离品转生产返工单执行","dispositionEvidence":"隔离标识已核对，待生产模块建立返工单","dispositionOwner":"周启明"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTION_REQUIRED"));

		String firstPlan = """
				{"action":"PLAN_ACTION","expectedVersion":2,"rootCause":"扭矩工具点检频次不足","correctiveAction":"增加班前扭矩校验并培训操作员","actionOwner":"周启明","actionDueDate":"%s"}
				""".formatted(LocalDate.now().plusDays(5));
		act(caseId, "nc-plan-1", firstPlan).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTION_IN_PROGRESS"));
		act(caseId, "nc-complete-action-1", """
				{"action":"COMPLETE_ACTION","expectedVersion":3,"actionCompletionEvidence":"新版点检表已发布，三名操作员完成培训"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFICATION_PENDING"));
		act(caseId, "nc-verify-failed-1", """
				{"action":"VERIFY","expectedVersion":4,"effective":false,"verificationConclusion":"抽查仍发现一台扭矩漂移，措施无效"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTION_REQUIRED"));

		String revisedPlan = """
				{"action":"PLAN_ACTION","expectedVersion":5,"rootCause":"工具校验方法缺少标准件","correctiveAction":"配置标准扭矩件并执行每班首件验证","actionOwner":"何工","actionDueDate":"%s"}
				""".formatted(LocalDate.now().plusDays(7));
		act(caseId, "nc-plan-2", revisedPlan).andExpect(status().isOk());
		act(caseId, "nc-complete-action-2", """
				{"action":"COMPLETE_ACTION","expectedVersion":6,"actionCompletionEvidence":"标准件已入库，连续三班首件验证合格"}
				""").andExpect(status().isOk());
		act(caseId, "nc-verify-success-1", """
				{"action":"VERIFY","expectedVersion":7,"effective":true,"verificationConclusion":"连续三班抽查无复发，措施有效"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
				.andExpect(jsonPath("$.closedAt").exists());

		mockMvc.perform(get("/api/v1/quality/nonconformances/{id}", caseId).with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(9))
				.andExpect(jsonPath("$.events[4].action").value("COMPLETE_ACTION"));
		assertThat(jdbcTemplate.queryForObject("select count(*) from quality.nonconformances where inspection_id=cast(? as uuid)",
				Integer.class, inspectionId)).isEqualTo(1);
	}

	@Test
	@Transactional
	void supportsNoCapaVerificationReopenPermissionsAndWorkspaceIsolation() throws Exception {
		String inspectionId = UUID.randomUUID().toString();
		insertPendingInspection(inspectionId, EAST, "FINAL", "PRODUCTION_REPORT", null, null, null);
		completeFinal(inspectionId, "nc-no-capa-inspection");
		String caseId = caseIdFor(inspectionId);

		act(caseId, "nc-no-capa-review", """
				{"action":"REVIEW","expectedVersion":0,"severity":"LOW","immediateContainment":"隔离单件","reviewConclusion":"偶发外观缺陷，无系统性趋势","capaRequired":false}
				""").andExpect(status().isOk());
		act(caseId, "nc-no-capa-dispose", """
				{"action":"DISPOSE","expectedVersion":1,"dispositionType":"SCRAP","dispositionDecision":"单件报废","dispositionEvidence":"隔离标签和报废申请编号 SCRAP-PENDING-1 已登记","dispositionOwner":"周启明"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFICATION_PENDING"));
		act(caseId, "nc-no-capa-verify", """
				{"action":"VERIFY","expectedVersion":2,"effective":true,"verificationConclusion":"隔离责任证据完整，准予关闭质量记录"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
		act(caseId, "nc-no-capa-reopen", """
				{"action":"REOPEN","expectedVersion":3,"reason":"后续抽查发现同类缺陷再次出现"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));

		jdbcTemplate.update("update identity.workspace_memberships set role_code='PRODUCTION_OPERATOR' where id='30000000-0000-4000-8000-000000000101'");
		try {
			mockMvc.perform(get("/api/v1/quality/nonconformances").with(httpBasic(USERNAME, PASSWORD)))
					.andExpect(status().isForbidden());
		} finally {
			jdbcTemplate.update("update identity.workspace_memberships set role_code='ADMIN' where id='30000000-0000-4000-8000-000000000101'");
		}

		String southInspection = UUID.randomUUID().toString();
		insertPendingInspection(southInspection, SOUTH, "FINAL", "PRODUCTION_REPORT", null, null, null);
		jdbcTemplate.update("update identity.user_workspace_preferences set current_workspace_id=cast(? as uuid) where user_id=cast(? as uuid)", SOUTH, USER);
		entityManager.clear();
		try {
			completeFinal(southInspection, "nc-south-inspection");
			String southCase = caseIdFor(southInspection);
			jdbcTemplate.update("update identity.user_workspace_preferences set current_workspace_id=cast(? as uuid) where user_id=cast(? as uuid)", EAST, USER);
			entityManager.clear();
			mockMvc.perform(get("/api/v1/quality/nonconformances/{id}", southCase).with(httpBasic(USERNAME, PASSWORD)))
					.andExpect(status().isNotFound());
		} finally {
			jdbcTemplate.update("update identity.user_workspace_preferences set current_workspace_id=cast(? as uuid) where user_id=cast(? as uuid)", EAST, USER);
			entityManager.clear();
		}
	}

	private org.springframework.test.web.servlet.ResultActions act(String caseId, String requestId, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/quality/nonconformances/{id}/actions", caseId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void completeFinal(String inspectionId, String requestId) throws Exception {
		mockMvc.perform(post("/api/v1/quality/final-inspections/{id}/complete", inspectionId)
				.with(httpBasic(USERNAME, PASSWORD)).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"acceptedQuantity":9,"rejectedQuantity":1,"inspector":"吴倩","defectDescription":"外观划伤","conclusion":"九件放行，一件隔离","expectedVersion":0}
					"""))
				.andExpect(status().isOk());
	}

	private String caseIdFor(String inspectionId) {
		return jdbcTemplate.queryForObject("select id::text from quality.nonconformances where inspection_id=cast(? as uuid)",
				String.class, inspectionId);
	}

	private void insertPendingInspection(String id, String workspace, String type, String sourceType,
			String supplierId, String supplierCode, String supplierName) {
		jdbcTemplate.update("""
			insert into quality.inspections (
			 id, tenant_organization_id, owning_organization_id, workspace_id, inspection_number, inspection_type,
			 source_type, source_id, source_number, order_id, order_number, supplier_id, supplier_code, supplier_name,
			 material_id, material_code, material_name, material_specification, unit, inspection_quantity, status,
			 request_id, version, created_by, created_at)
			values (cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid), 'FQI-NC-TEST-' || left(?, 8), ?,
			 ?, cast(? as uuid), 'RPT-NC-TEST', cast(? as uuid), 'MO-NC-TEST', cast(? as uuid), ?, ?,
			 cast(? as uuid), 'GS-800', '伺服驱动控制柜', 'GS-800 标准型', '台', 10, 'PENDING',
			 'nc-create-' || ?, 0, cast(? as uuid), current_timestamp)
			""", id, TENANT, ORGANIZATION, workspace, id, type, sourceType, UUID.randomUUID(), UUID.randomUUID(),
				supplierId, supplierCode, supplierName, UUID.fromString("42000000-0000-4000-8000-000000000001"), id, USER);
	}
}
