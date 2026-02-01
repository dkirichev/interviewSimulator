package net.k2ai.interviewSimulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.service.GeminiIntegrationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Controller
public class InterviewWebSocketController {

    private final GeminiIntegrationService geminiIntegrationService;
    private final SimpMessagingTemplate messagingTemplate;


    @MessageMapping("/interview/start")
    public void startInterview(@Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionIdStr = headerAccessor.getSessionId();
        log.info("Starting interview for WebSocket session: {}", sessionIdStr);

        String candidateName = payload.getOrDefault("candidateName", "").trim();
        String position = payload.getOrDefault("position", "").trim();
        String difficulty = payload.getOrDefault("difficulty", "Standard");
        String language = payload.getOrDefault("language", "en");
        String cvText = payload.get("cvText");

        // Validate required fields
        if (candidateName.isBlank()) {
            log.warn("Validation failed: candidateName is required");
            sendError(sessionIdStr, "Candidate name is required");
            return;
        }

        if (position.isBlank()) {
            log.warn("Validation failed: position is required");
            sendError(sessionIdStr, "Target position is required");
            return;
        }

        UUID interviewSessionId = geminiIntegrationService.startInterview(sessionIdStr, candidateName, position, difficulty, language, cvText);

        log.info("Interview started - WebSocket: {}, Interview Session: {}, Language: {}, CV provided: {}", 
                sessionIdStr, interviewSessionId, language, cvText != null && !cvText.isBlank());
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
