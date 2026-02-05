package net.k2ai.interviewSimulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.service.GeminiIntegrationService;
import net.k2ai.interviewSimulator.service.InputSanitizerService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Controller
public class InterviewWebSocketController {

    private static final Set<String> VALID_DIFFICULTIES = Set.of("Easy", "Standard", "Hard");
    private static final Set<String> VALID_LANGUAGES = Set.of("en", "bg");
    private static final Set<String> VALID_VOICES = Set.of("Algieba", "Kore", "Fenrir", "Despina");

    private final GeminiIntegrationService geminiIntegrationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final InputSanitizerService sanitizerService;


    @MessageMapping("/interview/start")
    public void startInterview(@Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionIdStr = headerAccessor.getSessionId();
        log.info("Starting interview for WebSocket session: {}", sessionIdStr);

        // Extract and sanitize inputs
        String candidateName = sanitizerService.sanitizeName(payload.getOrDefault("candidateName", ""));
        String position = sanitizerService.sanitizePosition(payload.getOrDefault("position", ""));
        String difficulty = payload.getOrDefault("difficulty", "Standard");
        String language = payload.getOrDefault("language", "en");
        String cvText = sanitizerService.sanitizeCvText(payload.get("cvText"));
        String voiceId = payload.get("voiceId");
        String interviewerNameEN = sanitizerService.sanitizeName(payload.get("interviewerNameEN"));
        String interviewerNameBG = sanitizerService.sanitizeName(payload.get("interviewerNameBG"));
        String userApiKey = payload.get("userApiKey");

        // Validate required fields
        if (candidateName == null || candidateName.isBlank()) {
            log.warn("Validation failed: candidateName is invalid or missing");
            sendError(sessionIdStr, "Candidate name is required and must contain only letters");
            return;
        }

        if (position == null || position.isBlank()) {
            log.warn("Validation failed: position is invalid or missing");
            sendError(sessionIdStr, "Target position is required");
            return;
        }

        // Validate enums
        if (!VALID_DIFFICULTIES.contains(difficulty)) {
            log.warn("Validation failed: invalid difficulty '{}'", difficulty);
            difficulty = "Standard";
        }

        if (!VALID_LANGUAGES.contains(language)) {
            log.warn("Validation failed: invalid language '{}'", language);
            language = "en";
        }

        if (voiceId == null || !VALID_VOICES.contains(voiceId)) {
            log.warn("Validation failed: invalid voiceId '{}'", voiceId);
            voiceId = "Algieba";
        }

        // Set default interviewer names if not provided
        if (interviewerNameEN == null || interviewerNameEN.isBlank()) {
            interviewerNameEN = "George";
        }
        if (interviewerNameBG == null || interviewerNameBG.isBlank()) {
            interviewerNameBG = "Георги";
        }

        UUID interviewSessionId = geminiIntegrationService.startInterview(
                sessionIdStr, candidateName, position, difficulty, language, cvText, 
                voiceId, interviewerNameEN, interviewerNameBG, userApiKey);

        log.info("Interview started - WebSocket: {}, Interview Session: {}, Language: {}, Voice: {}, CV provided: {}, User API key: {}", 
                sessionIdStr, interviewSessionId, language, voiceId, cvText != null && !cvText.isBlank(), userApiKey != null);
    }//startInterview


    @MessageMapping("/interview/audio")
    public void handleAudio(@Payload String base64Audio, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        geminiIntegrationService.sendAudioToGemini(sessionId, base64Audio);
    }//handleAudio


    @MessageMapping("/interview/end")
    public void endInterview(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("End interview requested for session: {}", sessionId);
        geminiIntegrationService.endInterview(sessionId);
    }//endInterview


    @MessageMapping("/interview/mic-off")
    public void micOff(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("Mic turned off for session: {}", sessionId);
        geminiIntegrationService.sendAudioStreamEnd(sessionId);
    }//micOff


    private void sendError(String sessionId, String message) {
        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/error",
                Map.of("error", message),
                createHeaders(sessionId)
        );
    }//sendError


    private org.springframework.messaging.MessageHeaders createHeaders(String sessionId) {
        org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor = 
                org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }//createHeaders

}//InterviewWebSocketController
