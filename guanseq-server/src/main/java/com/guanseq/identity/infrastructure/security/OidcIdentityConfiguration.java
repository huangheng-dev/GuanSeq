package com.guanseq.identity.infrastructure.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.guanseq.identity.api.ActiveIdentityProvider;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "guanseq.security.mode", havingValue = "oidc")
class OidcIdentityConfiguration {

	@Bean
	Converter<Jwt, AbstractAuthenticationToken> oidcJwtAuthenticationConverter(
			ActiveIdentityProvider identityProvider,
			@Value("${guanseq.security.oidc-username-claim:preferred_username}") String usernameClaim) {
		if (usernameClaim.isBlank()) {
			throw new IllegalStateException("guanseq.security.oidc-username-claim must not be blank");
		}
		return jwt -> {
			Object rawUsername = jwt.getClaims().get(usernameClaim);
			if (!(rawUsername instanceof String username) || username.isBlank()) {
				throw invalidToken("身份令牌缺少可用的用户标识");
			}
			String activeUsername = identityProvider.findActiveUsername(username)
					.orElseThrow(() -> invalidToken("用户不存在或已停用"));
			return new JwtAuthenticationToken(jwt, List.of(), activeUsername);
		};
	}

	private static OAuth2AuthenticationException invalidToken(String description) {
		return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null));
	}
}
