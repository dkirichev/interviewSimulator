package net.k2ai.interviewSimulator.service;

import net.k2ai.interviewSimulator.exception.RateLimitException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiter with named buckets.
 * Prevents brute-force and abuse of expensive endpoints.
 */
@Service
public class RateLimitService {

	private static final int DEFAULT_MAX_ATTEMPTS = 10;

	private static final long DEFAULT_WINDOW_MILLIS = 60_000;

	private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();


	/**
	 * Default bucket: 10 attempts per 60s. Kept for backwards compatibility.
	 */
	public void checkRateLimit(String ipAddress) {
		checkRateLimit("default", ipAddress, DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW_MILLIS);
	}//checkRateLimit


	/**
	 * Throws RateLimitException if the bucket+key exceeds maxAttempts within windowMs.
	 */
	public void checkRateLimit(String bucket, String key, int maxAttempts, long windowMillis) {
		long now = System.currentTimeMillis();
		String composite = bucket + ":" + key;

		RateLimitEntry entry = rateLimitMap.compute(composite, (k, existing) -> {
			if (existing == null || now - existing.windowStart > windowMillis) {
				return new RateLimitEntry(now, new AtomicInteger(1));
			}
			existing.count.incrementAndGet();
			return existing;
		});

		if (entry.count.get() > maxAttempts) {
			throw new RateLimitException("Too many requests. Please wait before trying again.");
		}
	}//checkRateLimit


	/**
	 * Evicts buckets whose window has long expired. Caps memory from accumulating
	 * one entry per distinct IP forever.
	 */
	@Scheduled(fixedRate = 10 * 60 * 1000)
	public void cleanup() {
		long now = System.currentTimeMillis();
		// Generous expiry: 1 hour is well past the longest single-bucket window (5 min).
		long expiryAfter = 60 * 60 * 1000L;
		rateLimitMap.entrySet().removeIf(e -> now - e.getValue().windowStart > expiryAfter);
	}//cleanup


	private static class RateLimitEntry {
		final long windowStart;
		final AtomicInteger count;

		RateLimitEntry(long windowStart, AtomicInteger count) {
			this.windowStart = windowStart;
			this.count = count;
		}
	}//RateLimitEntry

}//RateLimitService
