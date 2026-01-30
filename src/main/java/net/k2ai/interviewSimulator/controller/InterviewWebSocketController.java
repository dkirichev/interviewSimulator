package net.k2ai.interviewSimulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.service.GeminiIntegrationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Controller
public class InterviewWebSocketController {

    private final GeminiIntegrationService geminiIntegrationService;


    @MessageMapping("/interview/start")
    public void startInterview(@Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionIdStr = headerAccessor.getSessionId();
        log.info("Starting interview for WebSocket session: {}", sessionIdStr);

        String candidateName = payload.getOrDefault("candidateName", "Unknown");
        String position = payload.getOrDefault("position", "Software Developer");
        String difficulty = payload.getOrDefault("difficulty", "Standard");
        String language = payload.getOrDefault("language", "en");

        UUID interviewSessionId = geminiIntegrationService.startInterview(sessionIdStr, candidateName, position, difficulty, language);

        log.info("Interview started - WebSocket: {}, Interview Session: {}, Language: {}", sessionIdStr, interviewSessionId, language);
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

}//InterviewWebSocketController
