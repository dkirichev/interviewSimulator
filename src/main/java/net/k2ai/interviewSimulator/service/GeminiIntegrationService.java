package net.k2ai.interviewSimulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import net.k2ai.interviewSimulator.entity.InterviewFeedback;
import net.k2ai.interviewSimulator.exception.RateLimitException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Service
public class GeminiIntegrationService {

	private final GeminiConfig geminiConfig;

	private final InterviewService interviewService;

	private final SimpMessagingTemplate messagingTemplate;

	private final InterviewPromptService promptService;

	private final GradingService gradingService;

	// Maps WebSocket session ID to interview state
	private final Map<String, InterviewState> activeSessions = new ConcurrentHashMap<>();


	public UUID startInterview(String wsSessionId, String candidateName, String position, String difficulty, String language) {
		return startInterview(wsSessionId, candidateName, position, difficulty, language, null, null, null, null, null);
	}//startInterview


	public UUID startInterview(String wsSessionId, String candidateName, String position, String difficulty, String language, String cvText) {
		return startInterview(wsSessionId, candidateName, position, difficulty, language, cvText, null, null, null, null);
	}//startInterview


	public UUID startInterview(String wsSessionId, String candidateName, String position, String difficulty,
							   String language, String cvText, String voiceId, String interviewerNameEN, String interviewerNameBG) {
		return startInterview(wsSessionId, candidateName, position, difficulty, language, cvText, voiceId, interviewerNameEN, interviewerNameBG, null);
	}//startInterview


	public UUID startInterview(String wsSessionId, String candidateName, String position, String difficulty,
							   String language, String cvText, String voiceId, String interviewerNameEN,
							   String interviewerNameBG, String userApiKey) {
		// Create database session
		UUID interviewSessionId = interviewService.startSession(candidateName, position, difficulty);

		// Determine which API key to use
		String effectiveApiKey = determineApiKey(userApiKey);
		if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
			log.error("No API key available for session: {}", wsSessionId);
			sendToClient(wsSessionId, "/queue/error", Map.of(
					"message", "API key required. Please provide a valid Gemini API key.",
					"requiresApiKey", true
			));
			return interviewSessionId;
		}

		// Use provided voice or fall back to config default
		String effectiveVoice = (voiceId != null && !voiceId.isBlank()) ? voiceId : geminiConfig.getVoiceName();

		// Create Gemini client with the selected voice and effective API key
		GeminiLiveClient geminiClient = new GeminiLiveClient(effectiveApiKey, geminiConfig.getLiveModel(), effectiveVoice);

		// Generate system instruction for the AI interviewer (language-aware, with optional CV and custom names)
		String systemInstruction;
		if (interviewerNameEN != null && interviewerNameBG != null) {
			systemInstruction = promptService.generateInterviewerPrompt(position, difficulty, language, cvText, interviewerNameEN, interviewerNameBG);
		} else {
			systemInstruction = promptService.generateInterviewerPrompt(position, difficulty, language, cvText);
		}
		geminiClient.setSystemInstruction(systemInstruction);

		// Create interview state (store voice, instruction, and API key for potential reconnection)
		InterviewState state = new InterviewState(interviewSessionId, geminiClient, candidateName, position, difficulty, language);
		state.setVoiceId(effectiveVoice);
		state.setSystemInstruction(systemInstruction);
		state.setUserApiKey(effectiveApiKey);
		activeSessions.put(wsSessionId, state);

		// Setup callbacks
		setupGeminiCallbacks(wsSessionId, state);

		// Connect to Gemini
		geminiClient.connect();

		log.info("Started interview session {} with voice: {}, using {}",
				interviewSessionId, effectiveVoice, userApiKey != null ? "user API key" : "backend API key");

		return interviewSessionId;
	}//startInterview


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


	private void setupGeminiCallbacks(String wsSessionId, InterviewState state) {
		setupGeminiCallbacks(wsSessionId, state, true);
	}//setupGeminiCallbacks


	private void setupGeminiCallbacks(String wsSessionId, InterviewState state, boolean isNewSession) {
		GeminiLiveClient client = state.getGeminiClient();

		// When Gemini is ready
		client.setOnConnected(() -> {
			log.info("Gemini ready for session: {} (new: {})", wsSessionId, isNewSession);

			if (isNewSession) {
				// New session - send greeting to trigger AI to introduce itself
				sendToClient(wsSessionId, "/queue/status", Map.of(
						"type", "CONNECTED",
						"message", "AI interviewer ready"
				));
				String greeting = "bg".equals(state.getLanguage()) ? "Здравейте!" : "Hello!";
				client.sendText(greeting);
				log.debug("Sent initial greeting to trigger AI: {}", greeting);
			} else {
				// Resumed session - just notify reconnection complete
				state.setReconnecting(false);
				log.info("Session resumed successfully for: {}", wsSessionId);
				// Send any buffered audio
				state.flushBufferedAudio(client);
			}
		});

		// When receiving audio from Gemini
		client.setOnAudioReceived(audioData -> {
			String base64Audio = Base64.getEncoder().encodeToString(audioData);
			sendToClient(wsSessionId, "/queue/audio", Map.of(
					"data", base64Audio
			));
		});

		// When receiving text from Gemini (shouldn't happen in audio mode, but handle it)
		client.setOnTextReceived(text -> {
			log.debug("Received text from Gemini: {}", text);
			sendToClient(wsSessionId, "/queue/text", Map.of(
					"text", text
			));
		});

		// Input transcription (user's speech)
		client.setOnInputTranscript(transcript -> {
			state.appendUserTranscript(transcript);
			sendToClient(wsSessionId, "/queue/transcript", Map.of(
					"speaker", "user",
					"text", transcript
			));
		});

		// Output transcription (AI's speech) - accumulate for turn-end checking
		client.setOnOutputTranscript(transcript -> {
			state.appendAiTranscript(transcript);
			state.appendCurrentTurnTranscript(transcript);
			sendToClient(wsSessionId, "/queue/transcript", Map.of(
					"speaker", "ai",
					"text", transcript
			));
		});

		// When AI turn is complete - check accumulated transcript for conclusion
		client.setOnTurnComplete(() -> {
			String turnText = state.getCurrentTurnTranscript();
			state.clearCurrentTurnTranscript();

			log.info("AI turn complete. Turn text ({} chars): {}", turnText.length(),
					turnText.length() > 200 ? turnText.substring(0, 200) + "..." : turnText);

			sendToClient(wsSessionId, "/queue/status", Map.of(
					"type", "TURN_COMPLETE",
					"message", "AI finished speaking"
			));

			// Check if this turn contained conclusion phrases
			if (promptService.isInterviewConcluding(turnText)) {
				log.info("AI concluded interview - ending session: {}", wsSessionId);
				endInterviewInternal(wsSessionId, state);
			}
		});

		// When user interrupts
		client.setOnInterrupted(() -> {
			state.clearCurrentTurnTranscript();
			sendToClient(wsSessionId, "/queue/status", Map.of(
					"type", "INTERRUPTED",
					"message", "Generation interrupted"
			));
		});

		// On error - detect rate limit and invalid key errors
		client.setOnError(error -> {
			log.error("Gemini error for session {}: {}", wsSessionId, error);

			// Check for rate limit or invalid key errors
			if (error != null && error.startsWith("RATE_LIMIT:")) {
				sendToClient(wsSessionId, "/queue/error", Map.of(
						"message", error.substring("RATE_LIMIT:".length()),
						"rateLimited", true
				));
			} else if (error != null && error.startsWith("INVALID_KEY:")) {
				sendToClient(wsSessionId, "/queue/error", Map.of(
						"message", error.substring("INVALID_KEY:".length()),
						"invalidKey", true
				));
			} else {
				sendToClient(wsSessionId, "/queue/error", Map.of(
						"message", error
				));
			}
		});

		// Handle GoAway - server is about to close connection, trigger reconnection
		client.setOnGoAway(timeLeft -> {
			log.warn("GoAway received for session {}, time left: {}. Initiating reconnection...", wsSessionId, timeLeft);
			if (!state.isEnded() && !state.isReconnecting()) {
				initiateReconnection(wsSessionId, state);
			}
		});

		// On connection closed - attempt reconnection if unexpected
		client.setOnClosed(() -> {
			log.info("Gemini connection closed for session: {}", wsSessionId);
			if (!state.isEnded() && !state.isReconnecting()) {
				// Unexpected close - try to reconnect
				String handle = client.getSessionResumptionHandle();
				if (handle != null) {
					log.info("Attempting to reconnect with resumption handle...");
					initiateReconnection(wsSessionId, state);
				} else {
					sendToClient(wsSessionId, "/queue/status", Map.of(
							"type", "DISCONNECTED",
							"message", "Connection lost"
					));
				}
			}
		});
	}//setupGeminiCallbacks


	private void initiateReconnection(String wsSessionId, InterviewState state) {
		if (state.isEnded() || state.isReconnecting()) {
			return;
		}

		state.setReconnecting(true);
		String resumptionHandle = state.getGeminiClient().getSessionResumptionHandle();

		if (resumptionHandle == null) {
			log.error("Cannot reconnect - no resumption handle available for session: {}", wsSessionId);
			state.setReconnecting(false);
			sendToClient(wsSessionId, "/queue/status", Map.of(
					"type", "DISCONNECTED",
					"message", "Connection lost - no resumption token"
			));
			return;
		}

		log.info("Initiating session reconnection for: {}", wsSessionId);

		// Close old connection gracefully
		state.getGeminiClient().close();

		// Create new client with same configuration (use stored API key)
		String effectiveVoice = state.getVoiceId() != null ? state.getVoiceId() : geminiConfig.getVoiceName();
		String effectiveApiKey = state.getUserApiKey();
		GeminiLiveClient newClient = new GeminiLiveClient(effectiveApiKey, geminiConfig.getLiveModel(), effectiveVoice);
		newClient.setSystemInstruction(state.getSystemInstruction());

		// Update state with new client
		state.setGeminiClient(newClient);

		// Setup callbacks for resumed session
		setupGeminiCallbacks(wsSessionId, state, false);

		// Connect with resumption handle
		newClient.connect(resumptionHandle);
		log.info("Reconnection initiated with resumption handle for session: {}", wsSessionId);
	}//initiateReconnection


	public void sendAudioToGemini(String wsSessionId, String base64Audio) {
		InterviewState state = activeSessions.get(wsSessionId);
		if (state == null || state.isEnded()) {
			// Session ended/grading - silently ignore remaining audio packets from frontend
			log.debug("Ignoring audio for ended/missing session: {}", wsSessionId);
			return;
		}

		try {
			byte[] audioData = Base64.getDecoder().decode(base64Audio);

			// Buffer audio during reconnection
			if (state.isReconnecting()) {
				state.bufferAudio(audioData);
				return;
			}

			state.getGeminiClient().sendAudio(audioData);
		} catch (Exception e) {
			log.error("Failed to send audio for session: {}", wsSessionId, e);
		}
	}//sendAudioToGemini


	public void sendAudioStreamEnd(String wsSessionId) {
		InterviewState state = activeSessions.get(wsSessionId);
		if (state != null && !state.isEnded()) {
			state.getGeminiClient().sendAudioStreamEnd();
		}
	}//sendAudioStreamEnd


	public void endInterview(String wsSessionId) {
		InterviewState state = activeSessions.get(wsSessionId);
		if (state == null) {
			log.warn("No session found for WebSocket: {}", wsSessionId);
			return;
		}

		endInterviewInternal(wsSessionId, state);
	}//endInterview


	private void endInterviewInternal(String wsSessionId, InterviewState state) {
		if (state.isEnded()) {
			return;
		}

		state.setEnded(true);
		log.info("Ending interview for session: {}", state.getInterviewSessionId());

		// Close Gemini connection
		state.getGeminiClient().close();

		// Save transcript to database
		String fullTranscript = state.getFullTranscript();
		interviewService.appendTranscript(state.getInterviewSessionId(), fullTranscript);
		interviewService.finalizeSession(state.getInterviewSessionId());

		// Notify client to show loading/grading screen
		sendToClient(wsSessionId, "/queue/status", Map.of(
				"type", "GRADING",
				"message", "Interview ended. Analyzing your performance..."
		));

		// Trigger grading (async)
		triggerGrading(wsSessionId, state);

		// Remove from active sessions
		activeSessions.remove(wsSessionId);
	}//endInterviewInternal


	private void triggerGrading(String wsSessionId, InterviewState state) {
		new Thread(() -> {
			try {
				InterviewFeedback feedback = gradingService.gradeInterview(state.getInterviewSessionId(), state.getUserApiKey());

				Map<String, Object> reportData = new HashMap<>();
				reportData.put("sessionId", state.getInterviewSessionId().toString());
				reportData.put("overallScore", feedback.getOverallScore());
				reportData.put("communicationScore", feedback.getCommunicationScore());
				reportData.put("technicalScore", feedback.getTechnicalScore());
				reportData.put("confidenceScore", feedback.getConfidenceScore());
				reportData.put("strengths", feedback.getStrengths());
				reportData.put("improvements", feedback.getImprovements());
				reportData.put("detailedAnalysis", feedback.getDetailedAnalysis());
				reportData.put("verdict", feedback.getVerdict());
				reportData.put("transcript", state.getFullTranscript());

				sendToClient(wsSessionId, "/queue/report", reportData);
			} catch (RateLimitException e) {
				log.error("Rate limit exceeded during grading for session: {}", state.getInterviewSessionId());
				sendToClient(wsSessionId, "/queue/error", Map.of(
						"message", "API rate limit exceeded. Please use a new API key.",
						"rateLimited", true
				));
			} catch (Exception e) {
				log.error("Grading failed for session: {}", state.getInterviewSessionId(), e);
				sendToClient(wsSessionId, "/queue/error", Map.of(
						"message", "Failed to generate report. Please try again."
				));
			}
		}).start();
	}//triggerGrading


	private void sendToClient(String wsSessionId, String destination, Map<String, Object> payload) {
		log.debug("Sending to session {} destination {}: {}", wsSessionId, destination, payload);

		// Create headers targeting the specific WebSocket session
		SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		headerAccessor.setSessionId(wsSessionId);
		headerAccessor.setLeaveMutable(true);

		// Send to the user destination with proper session targeting
		messagingTemplate.convertAndSendToUser(
				wsSessionId,
				destination,
				payload,
				headerAccessor.getMessageHeaders()
		);
	}//sendToClient


	public void handleDisconnect(String wsSessionId) {
		InterviewState state = activeSessions.get(wsSessionId);
		if (state != null) {
			log.info("WebSocket disconnected, cleaning up session: {}", wsSessionId);
			state.getGeminiClient().close();
			activeSessions.remove(wsSessionId);
		}
	}//handleDisconnect


	// Inner class to track interview state
	private static class InterviewState {

		private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InterviewState.class);

		private final UUID interviewSessionId;

		private GeminiLiveClient geminiClient;

		private final String candidateName;

		private final String position;

		private final String difficulty;

		private final String language;

		private final StringBuilder userTranscript = new StringBuilder();

		private final StringBuilder aiTranscript = new StringBuilder();

		private final StringBuilder fullTranscript = new StringBuilder();

		private final StringBuilder currentTurnTranscript = new StringBuilder();

		private boolean ended = false;

		// For session resumption
		private boolean reconnecting = false;

		private String voiceId;

		private String systemInstruction;

		// User's API key (for PROD mode and reconnection)
		private String userApiKey;

		// Buffer for audio during reconnection
		private final java.util.List<byte[]> audioBuffer = new java.util.ArrayList<>();


		public InterviewState(UUID interviewSessionId, GeminiLiveClient geminiClient,
							  String candidateName, String position, String difficulty, String language) {
			this.interviewSessionId = interviewSessionId;
			this.geminiClient = geminiClient;
			this.candidateName = candidateName;
			this.position = position;
			this.difficulty = difficulty;
			this.language = language;
		}//InterviewState


		public UUID getInterviewSessionId() {
			return interviewSessionId;
		}//getInterviewSessionId


		public GeminiLiveClient getGeminiClient() {
			return geminiClient;
		}//getGeminiClient


		public void setGeminiClient(GeminiLiveClient geminiClient) {
			this.geminiClient = geminiClient;
		}//setGeminiClient


		public String getLanguage() {
			return language;
		}//getLanguage


		public synchronized void appendUserTranscript(String text) {
			userTranscript.append(text);
			fullTranscript.append("\n[Candidate]: ").append(text);
		}//appendUserTranscript


		public synchronized void appendAiTranscript(String text) {
			aiTranscript.append(text);
			fullTranscript.append("\n[Interviewer]: ").append(text);
		}//appendAiTranscript


		public synchronized void appendCurrentTurnTranscript(String text) {
			currentTurnTranscript.append(text);
		}//appendCurrentTurnTranscript


		public synchronized String getCurrentTurnTranscript() {
			return currentTurnTranscript.toString();
		}//getCurrentTurnTranscript


		public synchronized void clearCurrentTurnTranscript() {
			currentTurnTranscript.setLength(0);
		}//clearCurrentTurnTranscript


		public String getFullTranscript() {
			return fullTranscript.toString();
		}//getFullTranscript


		public boolean isEnded() {
			return ended;
		}//isEnded


		public void setEnded(boolean ended) {
			this.ended = ended;
		}//setEnded


		public boolean isReconnecting() {
			return reconnecting;
		}//isReconnecting


		public void setReconnecting(boolean reconnecting) {
			this.reconnecting = reconnecting;
		}//setReconnecting


		public String getVoiceId() {
			return voiceId;
		}//getVoiceId


		public void setVoiceId(String voiceId) {
			this.voiceId = voiceId;
		}//setVoiceId


		public String getSystemInstruction() {
			return systemInstruction;
		}//getSystemInstruction


		public void setSystemInstruction(String systemInstruction) {
			this.systemInstruction = systemInstruction;
		}//setSystemInstruction


		public String getUserApiKey() {
			return userApiKey;
		}//getUserApiKey


		public void setUserApiKey(String userApiKey) {
			this.userApiKey = userApiKey;
		}//setUserApiKey


		public synchronized void bufferAudio(byte[] audioData) {
			if (reconnecting) {
				audioBuffer.add(audioData);
			}
		}//bufferAudio


		public synchronized void flushBufferedAudio(GeminiLiveClient client) {
			if (!audioBuffer.isEmpty()) {
				log.info("Flushing {} buffered audio packets after reconnection", audioBuffer.size());
				for (byte[] audio : audioBuffer) {
					client.sendAudio(audio);
				}
				audioBuffer.clear();
			}
		}//flushBufferedAudio

	}//InterviewState

}//GeminiIntegrationService
