package net.k2ai.interviewSimulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import net.k2ai.interviewSimulator.entity.InterviewFeedback;
import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.exception.ModelAccessException;
import net.k2ai.interviewSimulator.exception.RateLimitException;
import net.k2ai.interviewSimulator.repository.InterviewFeedbackRepository;
import net.k2ai.interviewSimulator.repository.InterviewSessionRepository;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Service
public class GradingService {

	private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

	private final GeminiConfig geminiConfig;

	private final GeminiModelRotationService rotationService;

	private final InterviewSessionRepository sessionRepository;

	private final InterviewFeedbackRepository feedbackRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final OkHttpClient httpClient = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.build();


	/**
	 * Grade interview with in-memory transcript and optional user API key/language.
	 * In REVIEWER/PROD mode, retries with model/key rotation on rate limit or access errors.
	 */
	public InterviewFeedback gradeInterview(UUID sessionId, String transcript, String userApiKey, String language) {
		log.info("Starting grading for session: {}", sessionId);

		InterviewSession session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

		if (transcript == null || transcript.isBlank()) {
			log.warn("No transcript available for session: {}", sessionId);
			return saveFeedback(session, createDefaultFeedback(session));
		}

		// Determine effective language
		String effectiveLanguage = language;
		if (effectiveLanguage == null || effectiveLanguage.isBlank()) {
			effectiveLanguage = session.getLanguage() != null ? session.getLanguage() : "en";
		}

		String prompt = buildGradingPrompt(session, transcript, effectiveLanguage);

		// Use rotation for REVIEWER and PROD modes
		if (geminiConfig.isReviewerMode() || geminiConfig.isProdMode()) {
			return gradeWithRotation(session, prompt, userApiKey);
		}

		// DEV mode: simple single call
		return gradeSimple(session, prompt, userApiKey);
	}//gradeInterview


	/**
	 * Grading with model/key rotation (REVIEWER + PROD modes).
	 * Each failure flags the combo so the next call to getNextAvailable returns a different one.
	 * Loop exits when all combos are exhausted or one succeeds.
	 */
	private InterviewFeedback gradeWithRotation(InterviewSession session, String prompt, String userApiKey) {
		int attempt = 0;
		int safetyLimit = 20;

		GeminiModelRotationService.GradingConfig config;
		while ((config = rotationService.getNextAvailable(userApiKey)) != null && attempt < safetyLimit) {
			attempt++;
			log.info("Grading attempt {} with model: {}", attempt, config.model());

			try {
				String response = callGeminiApi(prompt, config.apiKey(), config.model());
				InterviewFeedback feedback = parseGradingResponse(response, session);
				InterviewFeedback saved = saveFeedback(session, feedback);
				log.info("Grading complete for session: {}. Score: {} (model: {})",
						session.getId(), saved.getOverallScore(), config.model());
				return saved;
			} catch (RateLimitException e) {
				String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
				boolean isDaily = msg.contains("daily") || msg.contains("per day") || msg.contains("per_day");
				rotationService.flagExhausted(config.apiKey(), config.model(), isDaily);
				log.warn("Rate limit/overload on model {}, rotating to next...", config.model());
			} catch (ModelAccessException e) {
				rotationService.flagInaccessible(config.apiKey(), config.model());
				log.warn("Model {} inaccessible, rotating to next...", config.model());
			} catch (Exception e) {
				// Flag with short cooldown so this combo is skipped next iteration
				rotationService.flagExhausted(config.apiKey(), config.model(), false);
				log.warn("Grading error on model {} (flagged 65s, rotating): {}", config.model(), e.getMessage());
			}
		}

		log.error("All grading attempts failed for session: {}", session.getId());
		return saveFeedback(session, createDefaultFeedback(session));
	}//gradeWithRotation


	/**
	 * Simple grading for DEV mode (single key, single model, no rotation).
	 */
	private InterviewFeedback gradeSimple(InterviewSession session, String prompt, String userApiKey) {
		String effectiveApiKey = userApiKey != null ? userApiKey : geminiConfig.getApiKey();
		if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
			throw new IllegalStateException("No API key available for grading");
		}

		try {
			String response = callGeminiApi(prompt, effectiveApiKey, geminiConfig.getGradingModel());
			InterviewFeedback feedback = parseGradingResponse(response, session);

			InterviewFeedback saved = saveFeedback(session, feedback);

			log.info("Grading complete for session: {}. Score: {}", session.getId(), saved.getOverallScore());
			return saved;
		} catch (RateLimitException e) {
			throw e;
		} catch (Exception e) {
			log.error("Failed to grade interview for session: {}: {}", session.getId(), e.getMessage());
			InterviewFeedback fallback = createDefaultFeedback(session);
			return saveFeedback(session, fallback);
		}
	}//gradeSimple


	private String buildGradingPrompt(InterviewSession session, String transcript, String language) {
		String languageInstruction = "bg".equals(language)
				? """
						
						ВАЖНО: Напиши ЦЕЛИЯ отговор на БЪЛГАРСКИ език.
						Всички стойности в JSON трябва да бъдат на български:
						- "strengths" масивът трябва да е на български
						- "improvements" масивът трябва да е на български
						- "detailedAnalysis" трябва да е на български
						- "verdict" трябва да остане на английски (STRONG_HIRE, HIRE, MAYBE или NO_HIRE)
						"""
				: "";

		return String.format("""
						You are an expert interview evaluator. Analyze the following job interview transcript and provide a detailed evaluation.
						%s
						## Interview Details
						- Position: %s
						- Difficulty Level: %s
						- Candidate Name: %s
						
						## Transcript
						%s
						
						## Evaluation Instructions
						Evaluate the candidate's performance and provide scores from 0-100 for each category.
						Be fair but honest in your assessment. Consider the difficulty level in your evaluation.
						
						Respond with ONLY a JSON object in exactly this format:
						{
						    "overallScore": <0-100>,
						    "communicationScore": <0-100>,
						    "technicalScore": <0-100>,
						    "confidenceScore": <0-100>,
						    "strengths": ["strength1", "strength2", "strength3"],
						    "improvements": ["improvement1", "improvement2", "improvement3"],
						    "detailedAnalysis": "A paragraph providing detailed feedback about the candidate's performance, what they did well, and specific areas for improvement.",
						    "verdict": "<STRONG_HIRE|HIRE|MAYBE|NO_HIRE>"
						}
						
						Important:
						- overallScore should reflect the overall interview performance
						- communicationScore evaluates clarity, articulation, and listening skills
						- technicalScore evaluates domain knowledge and problem-solving
						- confidenceScore evaluates composure, assertiveness, and presence
						- Provide 2-4 specific strengths observed
						- Provide 2-4 actionable improvements
						- detailedAnalysis should be 2-4 sentences with constructive feedback
						- verdict should match the overall assessment
						""",
				languageInstruction,
				session.getJobPosition(),
				session.getDifficulty(),
				session.getCandidateName(),
				transcript
		);
	}//buildGradingPrompt


	private InterviewFeedback saveFeedback(InterviewSession session, InterviewFeedback feedback) {
		InterviewFeedback saved = feedbackRepository.save(feedback);
		session.setScore(saved.getOverallScore());
		sessionRepository.save(session);
		return saved;
	}//saveFeedback


	private String callGeminiApi(String prompt, String apiKey, String model) throws IOException {
		String url = String.format(GEMINI_API_URL, model, apiKey);

		String requestBody = String.format("""
				{
				    "contents": [{
				        "parts": [{
				            "text": %s
				        }]
				    }],
				    "generationConfig": {
				        "temperature": 0.7,
				        "maxOutputTokens": 8192,
				        "responseMimeType": "application/json"
				    }
				}
				""", objectMapper.writeValueAsString(prompt));

		Request request = new Request.Builder()
				.url(url)
				.post(RequestBody.create(requestBody, MediaType.parse("application/json")))
				.build();

		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				String errorBody = response.body() != null ? response.body().string() : "No response body";
				log.error("Gemini API error: {} - {}", response.code(), errorBody);

				// Rate limit (429) or temporary overload (503)
				if (response.code() == 429 || response.code() == 503) {
					throw new RateLimitException("API rate limit/overload (" + response.code() + "): " + errorBody);
				}

				// Model inaccessible: bad key (401), permission denied (403), not found (404),
				// or billing/precondition failure (400 FAILED_PRECONDITION)
				if (response.code() == 401 || response.code() == 403 || response.code() == 404) {
					throw new ModelAccessException("Model not accessible: " + model + " - " + response.code());
				}
				if (response.code() == 400 && errorBody.contains("FAILED_PRECONDITION")) {
					throw new ModelAccessException("Model precondition failed (billing/access): " + model);
				}

				throw new IOException("Gemini API error: " + response.code());
			}

			String responseBody = response.body().string();
			log.debug("Gemini grading response: {}", responseBody);
			return responseBody;
		}
	}//callGeminiApi


	private InterviewFeedback parseGradingResponse(String response, InterviewSession session) {
		try {
			JsonNode root = objectMapper.readTree(response);

			// Extract text from Gemini response
			JsonNode candidates = root.path("candidates");
			if (candidates.isEmpty()) {
				throw new RuntimeException("No candidates in response");
			}

			JsonNode candidate = candidates.get(0);

			// Check for MAX_TOKENS truncation
			String finishReason = candidate.path("finishReason").asText("");
			if ("MAX_TOKENS".equals(finishReason)) {
				log.warn("Grading response was truncated (MAX_TOKENS) for session: {}", session.getId());
				throw new RuntimeException("AI response was truncated - output token limit reached");
			}

			String text = candidate
					.path("content")
					.path("parts")
					.get(0)
					.path("text")
					.asText();

			// Extract JSON from the response (might be wrapped in markdown code blocks)
			String jsonStr = extractJson(text);
			JsonNode evaluation = objectMapper.readTree(jsonStr);

			return InterviewFeedback.builder()
					.session(session)
					.overallScore(evaluation.path("overallScore").asInt(50))
					.communicationScore(evaluation.path("communicationScore").asInt(50))
					.technicalScore(evaluation.path("technicalScore").asInt(50))
					.confidenceScore(evaluation.path("confidenceScore").asInt(50))
					.strengths(evaluation.path("strengths").toString())
					.improvements(evaluation.path("improvements").toString())
					.detailedAnalysis(evaluation.path("detailedAnalysis").asText("No detailed analysis available."))
					.verdict(evaluation.path("verdict").asText("MAYBE"))
					.build();
		} catch (Exception e) {
			log.error("Failed to parse grading response for session: {}: {}", session.getId(), e.getMessage());
			throw new RuntimeException("Failed to parse grading response: " + e.getMessage(), e);
		}
	}//parseGradingResponse


	private String extractJson(String text) {
		// Try to extract JSON from markdown code block
		if (text.contains("```json")) {
			int start = text.indexOf("```json") + 7;
			int end = text.indexOf("```", start);
			if (end > start) {
				return text.substring(start, end).trim();
			}
		}

		// Try to extract JSON from plain code block
		if (text.contains("```")) {
			int start = text.indexOf("```") + 3;
			int end = text.indexOf("```", start);
			if (end > start) {
				return text.substring(start, end).trim();
			}
		}

		// Try to find JSON object directly
		int braceStart = text.indexOf('{');
		int braceEnd = text.lastIndexOf('}');
		if (braceStart >= 0 && braceEnd > braceStart) {
			return text.substring(braceStart, braceEnd + 1);
		}

		return text;
	}//extractJson


	private InterviewFeedback createDefaultFeedback(InterviewSession session) {
		return InterviewFeedback.builder()
				.session(session)
				.overallScore(50)
				.communicationScore(50)
				.technicalScore(50)
				.confidenceScore(50)
				.strengths("[\"Unable to evaluate - insufficient data\"]")
				.improvements("[\"Complete the interview for full evaluation\"]")
				.detailedAnalysis("The interview could not be fully evaluated. Please ensure the interview is completed with sufficient dialogue for accurate assessment.")
				.verdict("MAYBE")
				.build();
	}//createDefaultFeedback

}//GradingService
