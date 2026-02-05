package net.k2ai.interviewSimulator.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service for sanitizing user input to prevent XSS, injection attacks, and other security issues.
 * Used to clean input before storing in database or sending to external services like Gemini.
 */
@Service
public class InputSanitizerService {

	private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
	private static final Pattern SCRIPT_PATTERN = Pattern.compile(
			"(?i)<script[^>]*>.*?</script>|javascript:|on\\w+\\s*=", Pattern.DOTALL
	);
	private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
			"(?i)(--|;|'|\"\\s*(or|and)\\s+|union\\s+select|insert\\s+into|delete\\s+from|drop\\s+table)",
			Pattern.CASE_INSENSITIVE
	);

	/**
	 * Sanitizes text for safe display in HTML.
	 * Escapes HTML special characters to prevent XSS.
	 */
	public String sanitizeForHtml(String input) {
		if (input == null) {
			return null;
		}
		return input
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#x27;");
	}

	/**
	 * Sanitizes text for use in prompts sent to AI services.
	 * Removes potentially dangerous content while preserving readability.
	 */
	public String sanitizeForPrompt(String input) {
		if (input == null) {
			return null;
		}

		String sanitized = input;

		// Remove script tags and javascript: URLs
		sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");

		// Remove HTML tags (keep content)
		sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");

		// Trim and normalize whitespace
		sanitized = sanitized.replaceAll("\\s+", " ").trim();

		return sanitized;
	}

	/**
	 * Sanitizes a candidate name - only allows letters, spaces, hyphens, and apostrophes.
	 * Returns null if input is invalid.
	 */
	public String sanitizeName(String input) {
		if (input == null || input.isBlank()) {
			return null;
		}

		String trimmed = input.trim();

		// Only allow letters (Unicode), spaces, hyphens, apostrophes
		if (!trimmed.matches("^[\\p{L}\\s\\-']+$")) {
			return null;
		}

		// Limit length
		if (trimmed.length() > 30) {
			trimmed = trimmed.substring(0, 30);
		}

		return trimmed;
	}

	/**
	 * Sanitizes a position/job title - allows letters, numbers, spaces, and basic punctuation.
	 * Returns null if input contains dangerous characters.
	 */
	public String sanitizePosition(String input) {
		if (input == null || input.isBlank()) {
			return null;
		}

		String trimmed = input.trim();

		// Check for dangerous patterns
		if (SQL_INJECTION_PATTERN.matcher(trimmed).find()) {
			return null;
		}
		if (SCRIPT_PATTERN.matcher(trimmed).find()) {
			return null;
		}

		// Only allow safe characters
		if (!trimmed.matches("^[\\p{L}\\p{N}\\s.,+\\-#()/&]+$")) {
			return null;
		}

		// Limit length
		if (trimmed.length() > 50) {
			trimmed = trimmed.substring(0, 50);
		}

		return trimmed;
	}

	/**
	 * Sanitizes CV text - removes dangerous content while preserving document structure.
	 */
	public String sanitizeCvText(String input) {
		if (input == null) {
			return null;
		}

		String sanitized = input;

		// Remove script tags and javascript
		sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");

		// Remove HTML tags
		sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");

		// Limit total length to prevent memory issues (100KB max)
		if (sanitized.length() > 100_000) {
			sanitized = sanitized.substring(0, 100_000);
		}

		return sanitized;
	}

	/**
	 * Validates that a value is one of the allowed values.
	 * Returns the value if valid, or the default value if not.
	 */
	public String validateEnum(String input, String[] allowedValues, String defaultValue) {
		if (input == null || input.isBlank()) {
			return defaultValue;
		}

		for (String allowed : allowedValues) {
			if (allowed.equals(input)) {
				return input;
			}
		}

		return defaultValue;
	}

}
