package com.guanseq.identity.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
		name = "guanseq.security.mode",
		havingValue = "development",
		matchIfMissing = false)
class DevelopmentIdentityConfiguration {

	@Bean
	UserDetailsService developmentUserDetailsService(
			@Value("${guanseq.security.development-username}") String username,
			@Value("${guanseq.security.development-password}") String password) {
		return new InMemoryUserDetailsManager(User.withUsername(username)
				.password("{noop}" + password)
				.roles("USER")
				.build());
	}
}
