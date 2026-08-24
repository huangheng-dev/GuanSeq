package com.guanseq.identity.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
		name = "guanseq.security.development-identity-enabled",
		havingValue = "false",
		matchIfMissing = true)
class DisabledIdentityConfiguration {

	@Bean
	UserDetailsService disabledUserDetailsService() {
		return new InMemoryUserDetailsManager();
	}
}
