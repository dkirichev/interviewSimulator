package net.k2ai.interviewSimulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Security configuration for the Interview Simulator.
 * Admin panel requires authentication at /admin/**.
 * All other routes remain public (interview tool).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {


	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}//passwordEncoder


	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
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
