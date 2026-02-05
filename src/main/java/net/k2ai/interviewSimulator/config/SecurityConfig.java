package net.k2ai.interviewSimulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Security configuration for the Interview Simulator.
 * No authentication required (public demo tool), but enables:
 * - CSRF protection for forms
 * - Security headers (XSS, clickjacking, content-type sniffing protection)
 * - Content Security Policy
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(auth -> auth
						.anyRequest().permitAll()
				)
				.csrf(csrf -> csrf
						.ignoringRequestMatchers("/ws/**", "/api/**")
				)
				.headers(headers -> headers
						.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
						.xssProtection(xss -> xss
								.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
						)
						.contentTypeOptions(contentType -> {
						})
						.referrerPolicy(referrer -> referrer
								.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
						)
						.httpStrictTransportSecurity(hsts -> hsts
								.maxAgeInSeconds(31536000)
								.includeSubDomains(true)
						)
						.contentSecurityPolicy(csp -> csp
								.policyDirectives(
										"default-src 'self'; " +
												"script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.tailwindcss.com https://cdn.jsdelivr.net; " +
												"style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
												"font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
												"img-src 'self' data:; " +
												"connect-src 'self' wss: ws: https://generativelanguage.googleapis.com https://cdn.jsdelivr.net; " +
												"media-src 'self' blob:; " +
												"frame-ancestors 'none';"
								)
						)
				)
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.anonymous(anonymous -> {
				})
				.build();
	}

}
