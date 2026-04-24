package net.k2ai.interviewSimulator.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.exception.RateLimitException;
import net.k2ai.interviewSimulator.service.ClientIpResolver;
import net.k2ai.interviewSimulator.service.RateLimitService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Captures client IP into WebSocket session attributes at handshake time and
 * rate-limits new handshakes per IP to protect against unauthenticated
 * WebSocket flooding (which would otherwise drain Gemini API quota).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

	private static final int MAX_HANDSHAKES_PER_MINUTE = 10;
	private static final long WINDOW_MS = 60_000;

	private final RateLimitService rateLimitService;
	private final ClientIpResolver clientIpResolver;


	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
								   WebSocketHandler wsHandler, Map<String, Object> attributes) {
		String clientIp = "unknown";
		if (request instanceof ServletServerHttpRequest servletRequest) {
			clientIp = clientIpResolver.resolve(servletRequest.getServletRequest());
		}
		attributes.put("clientIp", clientIp);

		try {
			rateLimitService.checkRateLimit("ws-handshake", clientIp, MAX_HANDSHAKES_PER_MINUTE, WINDOW_MS);
			return true;
		} catch (RateLimitException e) {
			log.warn("WS handshake rate limit exceeded for IP: {}", clientIp);
			if (response instanceof ServletServerHttpResponse servletResponse) {
				servletResponse.getServletResponse().setStatus(429);
			}
			return false;
		}
	}//beforeHandshake


	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
							   WebSocketHandler wsHandler, Exception exception) {
		// no-op
	}//afterHandshake

}//WebSocketHandshakeInterceptor
