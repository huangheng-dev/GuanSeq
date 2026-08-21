package com.guanseq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MaterialPlanningParameterIntegrationTest {
	private static final String USERNAME = "lin.hao";
	private static final String PASSWORD = "guanseq_dev";
	private final MockMvc mockMvc;

	MaterialPlanningParameterIntegrationTest(@Autowired WebApplicationContext context) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity()).build();
	}

	@Test
	@Transactional
	void listsAndVersionControlsLeadTime() throws Exception {
		mockMvc.perform(get("/api/v1/planning/material-parameters").with(httpBasic(USERNAME, PASSWORD)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.materialCode == 'BR-6204' && @.leadTimeDays == 12)]").exists())
				.andExpect(jsonPath("$.items[?(@.materialCode == 'MAT-HIDDEN')]").doesNotExist());
		mockMvc.perform(put("/api/v1/planning/material-parameters/42000000-0000-4000-8000-000000000003")
				.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"leadTimeDays\":10,\"expectedVersion\":0}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.leadTimeDays").value(10)).andExpect(jsonPath("$.version").value(1));
		mockMvc.perform(put("/api/v1/planning/material-parameters/42000000-0000-4000-8000-000000000003")
				.with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"leadTimeDays\":9,\"expectedVersion\":0}"))
				.andExpect(status().isConflict());
	}
}
