package com.guanseq.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OidcIdentityConfigurationTest {

	private final OidcIdentityConfiguration configuration = new OidcIdentityConfiguration();

	@Test
	void mapsConfiguredClaimOnlyWhenInternalUserIsActive() {
		var converter = configuration.oidcJwtAuthenticationConverter(
				username -> "lin.hao".equals(username) ? Optional.of(username) : Optional.empty(),
				"preferred_username");

		var authentication = (JwtAuthenticationToken) converter.convert(jwt(Map.of(
				"sub", "external-subject",
				"preferred_username", "lin.hao")));

		assertThat(authentication).isNotNull();
		assertThat(authentication.getName()).isEqualTo("lin.hao");
		assertThat(authentication.getAuthorities()).isEmpty();
	}

	@Test
	void rejectsMissingUnknownAndBlankIdentityClaims() {
		var converter = configuration.oidcJwtAuthenticationConverter(
				username -> Optional.empty(),
				"preferred_username");

		assertThatThrownBy(() -> converter.convert(jwt(Map.of("sub", "external-subject"))))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.hasMessageContaining("身份令牌缺少可用的用户标识");
		assertThatThrownBy(() -> converter.convert(jwt(Map.of(
				"sub", "external-subject",
				"preferred_username", "unknown.user"))))
				.isInstanceOf(OAuth2AuthenticationException.class)
				.hasMessageContaining("用户不存在或已停用");
		assertThatThrownBy(() -> configuration.oidcJwtAuthenticationConverter(
				username -> Optional.of(username), " "))
				.isInstanceOf(IllegalStateException.class);
	}

	private static Jwt jwt(Map<String, Object> claims) {
		return new Jwt(
				"test-token",
				Instant.now().minusSeconds(1),
				Instant.now().plusSeconds(300),
				Map.of("alg", "RS256"),
				claims);
	}
}
