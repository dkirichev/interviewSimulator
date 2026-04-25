package net.k2ai.interviewSimulator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Security configuration for the Interview Simulator.
 * Admin panel requires authentication at /admin/**.
 * All other routes remain public (interview tool).
 */
@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final AdminLoginRateLimitFilter adminLoginRateLimitFilter;


	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}//passwordEncoder


	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.addFilterBefore(adminLoginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/admin/login").permitAll()
						.requestMatchers("/admin/**").hasRole("ADMIN")
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
												// 'unsafe-inline' retained because Thymeleaf templates embed inline scripts; removing
												// it would require wiring CSP nonces into every template. 'unsafe-eval' dropped — not
												// needed at runtime and a major XSS amplifier.
												"script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com https://cdn.jsdelivr.net; " +
												"style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
												"font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
												"img-src 'self' data:; " +
												// Narrowed: plaintext ws: dropped; only TLS WebSockets and the specific APIs we use.
												"connect-src 'self' wss: https://generativelanguage.googleapis.com https://cdn.jsdelivr.net; " +
												"media-src 'self' blob:; " +
												"object-src 'none'; " +
												"base-uri 'self'; " +
												"form-action 'self'; " +
												"frame-ancestors 'none';"
								)
						)
				)
				.formLogin(form -> form
						.loginPage("/admin/login")
						.loginProcessingUrl("/admin/login")
						.defaultSuccessUrl("/admin/dashboard", true)
						.failureUrl("/admin/login?error=true")
						.permitAll()
				)
				.logout(logout -> logout
						.logoutUrl("/admin/logout")
						.logoutSuccessUrl("/admin/login?logout=true")
						.permitAll()
				)
				.anonymous(anonymous -> {
				})
				.build();
	}//filterChain

}//SecurityConfig
