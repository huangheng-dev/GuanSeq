package com.guanseq.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledIdentityConfigurationTest {

	@Test
	void rejectsEveryIdentityWhenDevelopmentAdapterIsDisabled() {
		var userDetailsService = new DisabledIdentityConfiguration().disabledUserDetailsService();

		assertThatThrownBy(() -> userDetailsService.loadUserByUsername("lin.hao"))
				.isInstanceOf(UsernameNotFoundException.class);
	}
}
