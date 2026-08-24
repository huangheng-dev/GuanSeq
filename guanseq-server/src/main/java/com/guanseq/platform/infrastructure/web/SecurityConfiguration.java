package com.guanseq.platform.infrastructure.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter errorWriter) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/api/v1/platform/status").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) -> errorWriter.write(
								response,
								org.springframework.http.HttpStatus.UNAUTHORIZED,
								"请先完成身份认证"))
						.accessDeniedHandler((request, response, exception) -> errorWriter.write(
								response,
								org.springframework.http.HttpStatus.FORBIDDEN,
								"当前身份无权执行此操作")))
				.httpBasic(basic -> { })
				.formLogin(AbstractHttpConfigurer::disable)
				.build();
	}
}
