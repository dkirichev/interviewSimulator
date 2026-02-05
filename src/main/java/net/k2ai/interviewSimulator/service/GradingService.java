package net.k2ai.interviewSimulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import net.k2ai.interviewSimulator.entity.InterviewFeedback;
import net.k2ai.interviewSimulator.entity.InterviewSession;
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

	private final InterviewSessionRepository sessionRepository;

	private final InterviewFeedbackRepository feedbackRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final OkHttpClient httpClient = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.build();


	/**
	 * Grade interview using backend API key (for DEV mode or backward compatibility)
	 */
	public InterviewFeedback gradeInterview(UUID sessionId) {
		return gradeInterview(sessionId, null);
	}//gradeInterview


	/**
	 * Grade interview with optional user-provided API key
	 */
	public InterviewFeedback gradeInterview(UUID sessionId, String userApiKey) {
		log.info("Starting grading for session: {}", sessionId);

		// Determine which API key to use
		String effectiveApiKey = determineApiKey(userApiKey);
		if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
			throw new IllegalStateException("No API key available for grading");
		}

		InterviewSession session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

		String transcript = session.getTranscript();
		if (transcript == null || transcript.isBlank()) {
			log.warn("No transcript available for session: {}", sessionId);
			return createDefaultFeedback(session);
		}

		try {
			String prompt = buildGradingPrompt(session);
			String response = callGeminiApi(prompt, effectiveApiKey);
			InterviewFeedback feedback = parseGradingResponse(response, session);

			// Save to database
			InterviewFeedback saved = feedbackRepository.save(feedback);

			// Update session with score
			session.setScore(saved.getOverallScore());
			sessionRepository.save(session);

			log.info("Grading complete for session: {}. Score: {}", sessionId, saved.getOverallScore());
			return saved;
		} catch (RateLimitException e) {
			// Re-throw rate limit exceptions for caller to handle
			throw e;
		} catch (Exception e) {
			log.error("Failed to grade interview for session: {}", sessionId, e);
			return createDefaultFeedback(session);
		}
	}//gradeInterview


	/**
	 * Determines which API key to use based on mode and availability
	 */
	private String determineApiKey(String userApiKey) {
		if (geminiConfig.isProdMode()) {
			// PROD mode - must use user-provided key
			return userApiKey;
		} else {
			// DEV mode - use backend key
			return geminiConfig.getApiKey();
		}
	}//determineApiKey


	private String buildGradingPrompt(InterviewSession session) {
		return String.format("""
						You are an expert interview evaluator. Analyze the following job interview transcript and provide a detailed evaluation.
						
						## Interview Details
						- Position: %s
						- Difficulty Level: %s
						- Candidate Name: %s
						
						## Transcript
						%s
						
						## Evaluation Instructions
						Evaluate the candidate's performance and provide scores from 0-100 for each category.
						Be fair but honest in your assessment. Consider the difficulty level in your evaluation.
						
						Provide your evaluation in the following JSON format ONLY (no other text):
						```json
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
						```
						
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
				session.getJobPosition(),
				session.getDifficulty(),
				session.getCandidateName(),
				session.getTranscript()
		);
	}//buildGradingPrompt


	private String callGeminiApi(String prompt, String apiKey) throws IOException {
		String url = String.format(GEMINI_API_URL, geminiConfig.getGradingModel(), apiKey);

		String requestBody = String.format("""
				{
				    "contents": [{
				        "parts": [{
				            "text": %s
				        }]
				    }],
				    "generationConfig": {
				        "temperature": 0.7,
				        "maxOutputTokens": 2048
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

				// Check for rate limit (429 RESOURCE_EXHAUSTED)
				if (response.code() == 429) {
					throw new RateLimitException("API rate limit exceeded: " + errorBody);
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

			String text = candidates.get(0)
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
			log.error("Failed to parse grading response", e);
			return createDefaultFeedback(session);
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
