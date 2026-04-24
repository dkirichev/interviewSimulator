package net.k2ai.interviewSimulator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.exception.RateLimitException;
import net.k2ai.interviewSimulator.service.ClientIpResolver;
import net.k2ai.interviewSimulator.service.RateLimitService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Throttles POST /admin/login per IP to slow brute-force of admin credentials.
 * Runs before Spring Security's UsernamePasswordAuthenticationFilter so the
 * BCrypt(cost=12) verification is never even attempted once the bucket trips.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AdminLoginRateLimitFilter extends OncePerRequestFilter {

	private static final int MAX_ATTEMPTS = 10;
	private static final long WINDOW_MS = 300_000; // 5 minutes

	private final RateLimitService rateLimitService;
	private final ClientIpResolver clientIpResolver;


	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!"POST".equalsIgnoreCase(request.getMethod()) || !"/admin/login".equals(request.getRequestURI())) {
			chain.doFilter(request, response);
			return;
		}

		String clientIp = clientIpResolver.resolve(request);
		try {
			rateLimitService.checkRateLimit("admin-login", clientIp, MAX_ATTEMPTS, WINDOW_MS);
		} catch (RateLimitException e) {
			log.warn("Admin login rate limit exceeded for IP: {}", clientIp);
			response.sendRedirect("/admin/login?error=true");
			return;
		}

		chain.doFilter(request, response);
	}//doFilterInternal

}//AdminLoginRateLimitFilter
