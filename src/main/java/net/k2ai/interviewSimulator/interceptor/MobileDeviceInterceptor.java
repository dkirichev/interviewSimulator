package net.k2ai.interviewSimulator.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class MobileDeviceInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		// Skip interceptor for static resources and error page itself
		String requestURI = request.getRequestURI();
		if (requestURI.startsWith("/css/") || requestURI.startsWith("/js/") || 
			requestURI.startsWith("/images/") || requestURI.startsWith("/favicon.ico") ||
			requestURI.equals("/error/mobile-not-supported")) {
			return true;
		}

		// Detect mobile device from User-Agent header
		String userAgent = request.getHeader("User-Agent");
		if (userAgent != null && isMobileDevice(userAgent)) {
			log.debug("Mobile device detected, redirecting to mobile-not-supported page");
			response.sendRedirect("/error/mobile-not-supported");
			return false;
		}

		return true;
	}

	private boolean isMobileDevice(String userAgent) {
		String ua = userAgent.toLowerCase();
		return ua.contains("android") || 
			   ua.contains("webos") || 
			   ua.contains("iphone") || 
			   ua.contains("ipad") || 
			   ua.contains("ipod") || 
			   ua.contains("blackberry") || 
			   ua.contains("iemobile") || 
			   ua.contains("opera mini");
	}
}
