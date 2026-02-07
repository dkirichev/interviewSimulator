package net.k2ai.interviewSimulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages AI model and API key rotation with error-based rate limit tracking.
 * Used in REVIEWER mode (multi-key + multi-model) and PROD mode (multi-model only).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GeminiModelRotationService {

	private final GeminiConfig geminiConfig;

	// Tracks exhausted (key, model) combos: "key:model" → expiry instant
	private final ConcurrentHashMap<String, Instant> exhaustedCombos = new ConcurrentHashMap<>();

	private static final Duration MINUTE_COOLDOWN = Duration.ofSeconds(65);


	/**
	 * Represents an available API key + model configuration for a grading request.
	 */
	public record GradingConfig(String apiKey, String model) {
	}


	/**
	 * Returns the next available (apiKey, model) pair for grading.
	 * In REVIEWER mode: iterates through key/model pairs, skipping exhausted ones.
	 * In PROD mode: iterates through models with the user's key, skipping exhausted ones.
	 * Returns null if all combos are exhausted.
	 */
	public GradingConfig getNextAvailable(String userApiKey) {
		List<String> models = geminiConfig.getGradingModelList();

		if (geminiConfig.isReviewerMode()) {
			List<String> keys = geminiConfig.getReviewerKeyList();
			// Each key is paired with a model by index
			int count = Math.min(keys.size(), models.size());
			for (int i = 0; i < count; i++) {
				String key = keys.get(i);
				String model = models.get(i);
				if (!isExhausted(key, model)) {
					log.debug("Rotation: using key #{} with model {}", i + 1, model);
					return new GradingConfig(key, model);
				}
			}
			// Also try cross-combinations as fallback
			for (String key : keys) {
				for (String model : models) {
					if (!isExhausted(key, model)) {
						log.debug("Rotation: cross-combo key with model {}", model);
						return new GradingConfig(key, model);
					}
				}
			}
			log.error("All reviewer key/model combinations are exhausted!");
			return null;
		} else if (geminiConfig.isProdMode() && userApiKey != null) {
			// PROD mode: user's key, try different models
			for (String model : models) {
				if (!isExhausted(userApiKey, model)) {
					return new GradingConfig(userApiKey, model);
				}
			}
			log.warn("All models exhausted for user key");
			return null;
		} else {
			// DEV mode: single key, single model
			return new GradingConfig(geminiConfig.getApiKey(), geminiConfig.getGradingModel());
		}
	}//getNextAvailable


	/**
	 * Flags a (key, model) combo as exhausted after a rate limit or access error.
	 *
	 * @param apiKey  the API key that was rate-limited
	 * @param model   the model that was rate-limited
	 * @param isDaily true if this is a daily limit (expires at midnight PT), false for per-minute
	 */
	public void flagExhausted(String apiKey, String model, boolean isDaily) {
		String comboKey = buildComboKey(apiKey, model);
		Instant expiry;

		if (isDaily) {
			// Expire at next midnight Pacific Time
			ZonedDateTime nowPT = ZonedDateTime.now(ZoneId.of("America/Los_Angeles"));
			ZonedDateTime midnightPT = nowPT.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("America/Los_Angeles"));
			expiry = midnightPT.toInstant();
			log.warn("Flagged key/model combo as DAILY exhausted until {} PT: model={}", midnightPT.toLocalTime(), model);
		} else {
			// Per-minute cooldown
			expiry = Instant.now().plus(MINUTE_COOLDOWN);
			log.warn("Flagged key/model combo as MINUTE exhausted for 65s: model={}", model);
		}

		exhaustedCombos.put(comboKey, expiry);
	}//flagExhausted


	/**
	 * Flags a model as inaccessible (e.g., 403, model not found) with a long cooldown.
	 */
	public void flagInaccessible(String apiKey, String model) {
		String comboKey = buildComboKey(apiKey, model);
		// 1 hour cooldown for inaccessible models
		Instant expiry = Instant.now().plus(Duration.ofHours(1));
		exhaustedCombos.put(comboKey, expiry);
		log.warn("Flagged key/model combo as INACCESSIBLE for 1 hour: model={}", model);
	}//flagInaccessible


	private boolean isExhausted(String apiKey, String model) {
		String comboKey = buildComboKey(apiKey, model);
		Instant expiry = exhaustedCombos.get(comboKey);
		if (expiry == null) {
			return false;
		}
		if (Instant.now().isAfter(expiry)) {
			// Cooldown expired, remove and allow
			exhaustedCombos.remove(comboKey);
			log.debug("Cooldown expired for model={}, now available", model);
			return false;
		}
		return true;
	}//isExhausted


	private String buildComboKey(String apiKey, String model) {
		// Use last 8 chars of key to avoid logging full keys
		String keySuffix = apiKey != null && apiKey.length() > 8
				? apiKey.substring(apiKey.length() - 8)
				: "unknown";
		return keySuffix + ":" + model;
	}//buildComboKey

}//GeminiModelRotationService
