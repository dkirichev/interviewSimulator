package net.k2ai.interviewSimulator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@RequiredArgsConstructor
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	// Caps on a single inbound STOMP message and on the server→client buffer.
	// Audio chunks are ~8KB base64; 512KB gives headroom for the initial
	// cvText payload (sanitized to 100KB by the backend) plus other fields,
	// without leaving memory wide open to a crafted message.
	private static final int MAX_MESSAGE_SIZE = 512 * 1024;
	private static final int MAX_SEND_BUFFER_SIZE = 1024 * 1024;

	private final WebSocketHandshakeInterceptor handshakeInterceptor;


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
	public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
		registration.setMessageSizeLimit(MAX_MESSAGE_SIZE);
		registration.setSendBufferSizeLimit(MAX_SEND_BUFFER_SIZE);
	}//configureWebSocketTransport


	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// WebSocket endpoint that browser connects to
		registry.addEndpoint("/ws/interview")
				.setAllowedOriginPatterns("*")
				.addInterceptors(handshakeInterceptor)
				.withSockJS();
	}//registerStompEndpoints

}//WebSocketConfig
