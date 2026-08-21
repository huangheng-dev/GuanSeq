package com.guanseq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MasterDataIntegrationTest {

	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";

	private final MockMvc mockMvc;
	private final JdbcTemplate jdbcTemplate;

	MasterDataIntegrationTest(
			@Autowired WebApplicationContext context,
			@Autowired JdbcTemplate jdbcTemplate) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void isolatesCreatesUpdatesDeactivatesAndAuditsCustomers() throws Exception {
		mockMvc.perform(get("/api/v1/masterdata/customers")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "customer-list-0001"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "customer-list-0001"))
				.andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.items[?(@.code == 'CUS-HIDDEN')]").doesNotExist());

		MvcResult created = mockMvc.perform(post("/api/v1/masterdata/customers")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "customer-create-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"code":"CUS-TEST-01","name":"测试客户","customerType":"ENTERPRISE","creditLevel":"B","contactName":"测试联系人","contactPhone":"138-0000-6999","owner":"林浩"}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(0))
				.andReturn();
		String customerId = extractId(created);

		mockMvc.perform(put("/api/v1/masterdata/customers/{id}", customerId)
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "customer-update-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"code":"CUS-TEST-01","name":"测试客户（已更新）","customerType":"ENTERPRISE","creditLevel":"A","contactName":"测试联系人","contactPhone":"138-0000-6999","owner":"沈妍","expectedVersion":0}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.name").value("测试客户（已更新）"));

		mockMvc.perform(put("/api/v1/masterdata/customers/{id}", customerId)
					.with(httpBasic(USERNAME, PASSWORD))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"code":"CUS-TEST-01","name":"过期修改","customerType":"ENTERPRISE","creditLevel":"A","owner":"沈妍","expectedVersion":0}
							"""))
				.andExpect(status().isConflict());

		mockMvc.perform(patch("/api/v1/masterdata/customers/batch")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "customer-deactivate-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"records":[{"id":"%s","expectedVersion":1}],"status":"INACTIVE"}
							""".formatted(customerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("INACTIVE"))
				.andExpect(jsonPath("$[0].version").value(2));

		Integer auditCount = jdbcTemplate.queryForObject(
				"""
				select count(*) from masterdata.change_events
				where entity_id = cast(? as uuid)
				  and action in ('CREATED', 'UPDATED', 'DEACTIVATED')
				  and request_id is not null
				""",
				Integer.class,
				customerId);
		assertThat(auditCount).isEqualTo(3);
	}

	@Test
	void isolatesAndMutatesMaterialsWithOptimisticVersioning() throws Exception {
		mockMvc.perform(get("/api/v1/masterdata/materials")
					.with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.items[?(@.code == 'MAT-HIDDEN')]").doesNotExist());

		MvcResult created = mockMvc.perform(post("/api/v1/masterdata/materials")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "material-create-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"code":"MAT-TEST-01","name":"测试物料","specification":"TEST-100","materialType":"RAW_MATERIAL","baseUnit":"件","procurementType":"BUY","owner":"唐工"}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(0))
				.andReturn();
		String materialId = extractId(created);

		mockMvc.perform(patch("/api/v1/masterdata/materials/batch")
					.with(httpBasic(USERNAME, PASSWORD))
					.header("X-Request-Id", "material-owner-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"records":[{"id":"%s","expectedVersion":0}],"owner":"顾工"}
							""".formatted(materialId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].owner").value("顾工"))
				.andExpect(jsonPath("$[0].version").value(1));
	}

	private static String extractId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		var matcher = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(body);
		if (!matcher.find()) throw new AssertionError("响应中缺少 id: " + body);
		return matcher.group(1);
	}
}
