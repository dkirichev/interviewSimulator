package net.k2ai.interviewSimulator.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP.
 *
 * When app.trust-forwarded-headers=true the first entry of X-Forwarded-For is
 * used (deploy behind a trusted reverse proxy). Otherwise the direct socket
 * address is used. Trusting the header unconditionally lets any caller spoof
 * their source IP and bypass any IP-based throttle.
 */
@Component
public class ClientIpResolver {

	private final boolean trustForwardedHeaders;


	public ClientIpResolver(@Value("${app.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
		this.trustForwardedHeaders = trustForwardedHeaders;
	}//ClientIpResolver


	public String resolve(HttpServletRequest request) {
		if (trustForwardedHeaders) {
			String xff = request.getHeader("X-Forwarded-For");
			if (xff != null && !xff.isBlank()) {
				return xff.split(",")[0].trim();
			}
		}
		return request.getRemoteAddr();
	}//resolve


	public String resolve(java.util.Map<String, Object> handshakeAttributes) {
		Object ip = handshakeAttributes.get("clientIp");
		return ip instanceof String s ? s : "unknown";
	}//resolve

}//ClientIpResolver
