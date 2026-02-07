package net.k2ai.interviewSimulator.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {

	@Value("${app.mode:DEV}")
	private String appMode;

	private String apiKey;

	private String liveModel = "gemini-2.0-flash-exp";

	private String gradingModel = "gemini-2.5-pro-preview-05-06";

	private String voiceName = "Aoede";

	// Comma-separated list of grading models for fallback rotation (PROD + REVIEWER)
	private String gradingModels;

	// Comma-separated list of reviewer API keys (REVIEWER mode only)
	private String reviewerKeys;


	@PostConstruct
	public void validate() {
		boolean isProdMode = "PROD".equalsIgnoreCase(appMode);
		boolean isReviewerMode = "REVIEWER".equalsIgnoreCase(appMode);

		if (isProdMode) {
			log.info("Running in PROD mode - users must provide their own API key");
		} else if (isReviewerMode) {
			if (reviewerKeys == null || reviewerKeys.isBlank()) {
				log.error("REVIEWER mode requires GEMINI_REVIEWER_KEYS to be configured!");
				throw new IllegalStateException("GEMINI_REVIEWER_KEYS environment variable required in REVIEWER mode");
			}
			log.info("Running in REVIEWER mode - using multi-key rotation with {} keys",
					getReviewerKeyList().size());
		} else {
			// DEV mode - require backend API key
			if (apiKey == null || apiKey.isBlank()) {
				log.error("Gemini API key not configured! Set GEMINI_API_KEY environment variable.");
				throw new IllegalStateException("GEMINI_API_KEY environment variable required in DEV mode");
			}
			log.info("Running in DEV mode - using backend API key");
		}

		log.info("Gemini configuration loaded - Mode: {}, Live model: {}, Grading model: {}, Voice: {}",
				appMode, liveModel, gradingModel, voiceName);
	}//validate


	public boolean isProdMode() {
		return "PROD".equalsIgnoreCase(appMode);
	}//isProdMode


	public boolean isReviewerMode() {
		return "REVIEWER".equalsIgnoreCase(appMode);
	}//isReviewerMode


	/**
	 * Returns the list of grading models for fallback rotation.
	 * Falls back to the single gradingModel if not configured.
	 */
	public List<String> getGradingModelList() {
		if (gradingModels != null && !gradingModels.isBlank()) {
			return Arrays.stream(gradingModels.split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.toList();
		}
		return List.of(gradingModel);
	}//getGradingModelList


	/**
	 * Returns the list of reviewer API keys.
	 */
	public List<String> getReviewerKeyList() {
		if (reviewerKeys != null && !reviewerKeys.isBlank()) {
			return Arrays.stream(reviewerKeys.split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.toList();
		}
		return List.of();
	}//getReviewerKeyList

}//GeminiConfig
