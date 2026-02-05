package net.k2ai.interviewSimulator.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.service.GeminiIntegrationService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@RequiredArgsConstructor
@Component
public class WebSocketEventListener {

	private final GeminiIntegrationService geminiIntegrationService;


	@EventListener
	public void handleWebSocketConnected(SessionConnectedEvent event) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = headerAccessor.getSessionId();
		log.info("WebSocket connected: {}", sessionId);
	}//handleWebSocketConnected


	@EventListener
	public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = headerAccessor.getSessionId();
		log.info("WebSocket disconnected: {}", sessionId);

		// Cleanup any active interview session
		geminiIntegrationService.handleDisconnect(sessionId);
	}//handleWebSocketDisconnect

}//WebSocketEventListener
