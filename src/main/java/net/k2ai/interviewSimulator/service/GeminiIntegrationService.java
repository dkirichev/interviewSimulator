package net.k2ai.interviewSimulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import net.k2ai.interviewSimulator.entity.InterviewFeedback;
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
        return startInterview(wsSessionId, candidateName, position, difficulty, language, null);
    }//startInterview


    public UUID startInterview(String wsSessionId, String candidateName, String position, String difficulty, String language, String cvText) {
        // Create database session
        UUID interviewSessionId = interviewService.startSession(candidateName, position, difficulty);

        // Create Gemini client
        GeminiLiveClient geminiClient = new GeminiLiveClient(geminiConfig.getApiKey(), geminiConfig.getLiveModel(), geminiConfig.getVoiceName());

        // Generate system instruction for the AI interviewer (language-aware, with optional CV)
        String systemInstruction = promptService.generateInterviewerPrompt(position, difficulty, language, cvText);
        geminiClient.setSystemInstruction(systemInstruction);

        // Create interview state
        InterviewState state = new InterviewState(interviewSessionId, geminiClient, candidateName, position, difficulty, language);
        activeSessions.put(wsSessionId, state);

        // Setup callbacks
        setupGeminiCallbacks(wsSessionId, state);

        // Connect to Gemini
        geminiClient.connect();

        return interviewSessionId;
    }//startInterview


    private void setupGeminiCallbacks(String wsSessionId, InterviewState state) {
        GeminiLiveClient client = state.getGeminiClient();

        // When Gemini is ready - send initial greeting to trigger AI to speak first
        client.setOnConnected(() -> {
            log.info("Gemini ready for session: {}", wsSessionId);
            sendToClient(wsSessionId, "/queue/status", Map.of(
                    "type", "CONNECTED",
                    "message", "AI interviewer ready"
            ));

            // Send greeting to trigger AI to introduce itself
            String greeting = "bg".equals(state.getLanguage()) ? "Здравейте!" : "Hello!";
            client.sendText(greeting);
            log.debug("Sent initial greeting to trigger AI: {}", greeting);
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

        // On error
        client.setOnError(error -> {
            log.error("Gemini error for session {}: {}", wsSessionId, error);
            sendToClient(wsSessionId, "/queue/error", Map.of(
                    "message", error
            ));
        });

        // On connection closed
        client.setOnClosed(() -> {
            log.info("Gemini connection closed for session: {}", wsSessionId);
            if (!state.isEnded()) {
                sendToClient(wsSessionId, "/queue/status", Map.of(
                        "type", "DISCONNECTED",
                        "message", "Connection lost"
                ));
            }
        });
    }//setupGeminiCallbacks


    public void sendAudioToGemini(String wsSessionId, String base64Audio) {
        InterviewState state = activeSessions.get(wsSessionId);
        if (state == null || state.isEnded()) {
            // Session ended/grading - silently ignore remaining audio packets from frontend
            log.debug("Ignoring audio for ended/missing session: {}", wsSessionId);
            return;
        }

        try {
            byte[] audioData = Base64.getDecoder().decode(base64Audio);
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
                InterviewFeedback feedback = gradingService.gradeInterview(state.getInterviewSessionId());

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

        private final UUID interviewSessionId;

        private final GeminiLiveClient geminiClient;

        private final String candidateName;

        private final String position;

        private final String difficulty;

        private final String language;

        private final StringBuilder userTranscript = new StringBuilder();

        private final StringBuilder aiTranscript = new StringBuilder();

        private final StringBuilder fullTranscript = new StringBuilder();

        private final StringBuilder currentTurnTranscript = new StringBuilder();

        private boolean ended = false;


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

    }//InterviewState

}//GeminiIntegrationService
