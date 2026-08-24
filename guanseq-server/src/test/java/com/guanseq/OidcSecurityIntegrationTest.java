package com.guanseq;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.guanseq.platform.infrastructure.web.RequestIdFilter;

@Testcontainers(disabledWithoutDocker = true)
@Import({ TestcontainersConfiguration.class, OidcSecurityIntegrationTest.TestJwtConfiguration.class })
@SpringBootTest(properties = "guanseq.security.mode=oidc")
class OidcSecurityIntegrationTest {

	private final MockMvc mockMvc;

	OidcSecurityIntegrationTest(@Autowired WebApplicationContext context) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(context.getBean(RequestIdFilter.class))
				.apply(springSecurity())
				.build();
	}

	@Test
	void acceptsBearerOnlyAfterJwtMapsToActiveInternalUser() throws Exception {
		mockMvc.perform(get("/api/v1/me/workspaces")
					.header("Authorization", "Bearer active-user")
					.header("X-Request-Id", "oidc-active-0001"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "oidc-active-0001"))
				.andExpect(jsonPath("$.username").value("lin.hao"));

		mockMvc.perform(get("/api/v1/me/workspaces")
					.header("Authorization", "Bearer unknown-user")
					.header("X-Request-Id", "oidc-unknown-0001"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.requestId").value("oidc-unknown-0001"));

		mockMvc.perform(get("/api/v1/me/workspaces")
					.header("Authorization", "Basic bGluLmhhbzpndWFuc2VxX2Rldg=="))
				.andExpect(status().isUnauthorized());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestJwtConfiguration {

		@Bean
		@Primary
		JwtDecoder testJwtDecoder() {
			return token -> switch (token) {
				case "active-user" -> jwt(token, "lin.hao");
				case "unknown-user" -> jwt(token, "unknown.user");
				default -> throw new BadJwtException("invalid test token");
			};
		}

		private static Jwt jwt(String token, String username) {
			return new Jwt(
					token,
					Instant.now().minusSeconds(1),
					Instant.now().plusSeconds(300),
					Map.of("alg", "RS256"),
					Map.of("sub", "external-subject", "preferred_username", username));
		}
	}
}
