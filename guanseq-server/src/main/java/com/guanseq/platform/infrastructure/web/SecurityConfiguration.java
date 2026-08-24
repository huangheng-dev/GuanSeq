package com.guanseq.platform.infrastructure.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ApiErrorWriter errorWriter,
			@Value("${guanseq.security.mode:disabled}") String securityMode,
			ObjectProvider<Converter<Jwt, AbstractAuthenticationToken>> jwtConverterProvider) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/api/v1/platform/status").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) -> errorWriter.write(
								response,
								HttpStatus.UNAUTHORIZED,
								"请先完成身份认证"))
						.accessDeniedHandler((request, response, exception) -> errorWriter.write(
								response,
								HttpStatus.FORBIDDEN,
								"当前身份无权执行此操作")))
				.formLogin(AbstractHttpConfigurer::disable);

		switch (securityMode) {
			case "development" -> http.httpBasic(basic -> { });
			case "oidc" -> http.oauth2ResourceServer(oauth2 -> oauth2
					.authenticationEntryPoint((request, response, exception) -> errorWriter.write(
							response,
							HttpStatus.UNAUTHORIZED,
							"身份令牌无效或已经过期"))
					.accessDeniedHandler((request, response, exception) -> errorWriter.write(
							response,
							HttpStatus.FORBIDDEN,
							"当前身份无权执行此操作"))
					.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverterProvider.getObject())));
			case "disabled" -> { }
			default -> throw new IllegalStateException(
					"Unsupported guanseq.security.mode: " + securityMode
							+ ". Expected disabled, development, or oidc.");
		}

		return http.build();
	}
}
