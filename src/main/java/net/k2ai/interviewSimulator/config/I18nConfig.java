package net.k2ai.interviewSimulator.config;

import lombok.RequiredArgsConstructor;
import net.k2ai.interviewSimulator.interceptor.MobileDeviceInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.Locale;

@RequiredArgsConstructor
@Configuration
public class I18nConfig implements WebMvcConfigurer {

	private final MobileDeviceInterceptor mobileDeviceInterceptor;

	@Bean
	public LocaleResolver localeResolver() {
		CookieLocaleResolver resolver = new CookieLocaleResolver("ui_lang");
		resolver.setDefaultLocale(new Locale("bg")); // Bulgarian as default
		resolver.setCookieMaxAge(Duration.ofDays(365)); // Cache for 1 year
		resolver.setCookiePath("/");
		return resolver;
	}// localeResolver


	@Bean
	public LocaleChangeInterceptor localeChangeInterceptor() {
		LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
		interceptor.setParamName("lang");
		return interceptor;
	}// localeChangeInterceptor


	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// Mobile device blocker - must be first
		registry.addInterceptor(mobileDeviceInterceptor);
		// Locale switcher
		registry.addInterceptor(localeChangeInterceptor());
	}// addInterceptors
}// WebMvcConfigurer
