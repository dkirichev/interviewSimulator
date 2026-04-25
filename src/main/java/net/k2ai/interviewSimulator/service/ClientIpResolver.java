package net.k2ai.interviewSimulator.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the real client IP for per-IP rate limiting.
 *
 * <p>When {@code app.trust-forwarded-headers=true} the proxy headers are honored
 * in this priority:
 * <ol>
 *   <li>{@code CF-Connecting-IP} — set by Cloudflare on every proxied request.
 *       Cloudflare overwrites any client-supplied value, so this is the
 *       authoritative visitor IP and cannot be spoofed by the caller.</li>
 *   <li>{@code X-Forwarded-For} (leftmost entry) — generic reverse-proxy header,
 *       used as fallback for non-Cloudflare deployments.</li>
 *   <li>{@code request.getRemoteAddr()} — direct socket peer.</li>
 * </ol>
 *
 * <p>When the flag is false the direct socket address is always used. Trusting
 * forwarded headers without a proxy in front lets any caller spoof their source
 * IP and bypass IP-based throttles.
 */
@Component
public class ClientIpResolver {

	private static final String HEADER_CF_CONNECTING_IP = "CF-Connecting-IP";
	private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

	private final boolean trustForwardedHeaders;


	public ClientIpResolver(@Value("${app.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
		this.trustForwardedHeaders = trustForwardedHeaders;
	}//ClientIpResolver


	public String resolve(HttpServletRequest request) {
		if (trustForwardedHeaders) {
			String cfIp = request.getHeader(HEADER_CF_CONNECTING_IP);
			if (cfIp != null && !cfIp.isBlank()) {
				return cfIp.trim();
			}
			String xff = request.getHeader(HEADER_X_FORWARDED_FOR);
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
