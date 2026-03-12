package net.k2ai.interviewSimulator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {


	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		// Enable simple broker for topics (broadcast) and queues (user-specific)
		config.enableSimpleBroker("/topic", "/queue");
		// Prefix for messages FROM client TO server
		config.setApplicationDestinationPrefixes("/app");
		// Prefix for user-specific messages
		config.setUserDestinationPrefix("/user");
	}//configureMessageBroker


	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// WebSocket endpoint that browser connects to
		// In production, restrict to your domain via ALLOWED_ORIGINS env var
		String allowedOrigins = System.getenv("ALLOWED_ORIGINS");
		String[] origins = (allowedOrigins != null && !allowedOrigins.isBlank())
				? allowedOrigins.split(",")
				: new String[]{"http://localhost:8080", "http://127.0.0.1:8080"};

		registry.addEndpoint("/ws/interview")
				.setAllowedOriginPatterns(origins)
				.withSockJS();
	}//registerStompEndpoints

}//WebSocketConfig
