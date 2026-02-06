package net.k2ai.interviewSimulator.service;

import net.k2ai.interviewSimulator.exception.RateLimitException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for API key validation attempts.
 * Prevents brute-force API key testing.
 */
@Service
public class RateLimitService {

	private static final int MAX_ATTEMPTS_PER_WINDOW = 10;

	private static final long WINDOW_MILLIS = 60_000; // 1 minute

	private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();


	/**
	 * Check if an IP address is rate limited and increment the counter.
	 *
	 * @param ipAddress The client IP address
	 * @throws RateLimitException if rate limit exceeded
	 */
	public void checkRateLimit(String ipAddress) {
		long now = System.currentTimeMillis();

		RateLimitEntry entry = rateLimitMap.compute(ipAddress, (key, existing) -> {
			if (existing == null || now - existing.windowStart > WINDOW_MILLIS) {
				// Start new window
				return new RateLimitEntry(now, new AtomicInteger(1));
			} else {
				// Increment existing
				existing.count.incrementAndGet();
				return existing;
			}
		});

		if (entry.count.get() > MAX_ATTEMPTS_PER_WINDOW) {
			throw new RateLimitException("Too many requests. Please wait a minute before trying again.");
		}
	}// checkRateLimit


	/**
	 * Periodically clean up old entries.
	 */
	public void cleanup() {
		long now = System.currentTimeMillis();
		rateLimitMap.entrySet().removeIf(e -> now - e.getValue().windowStart > WINDOW_MILLIS * 2);
	}// cleanup


	private static class RateLimitEntry {
		final long windowStart;
		final AtomicInteger count;

		RateLimitEntry(long windowStart, AtomicInteger count) {
			this.windowStart = windowStart;
			this.count = count;
		}
	}// RateLimitEntry

}// RateLimitService
