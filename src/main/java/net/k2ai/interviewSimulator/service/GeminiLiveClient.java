package net.k2ai.interviewSimulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class GeminiLiveClient {
    private static final String WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent";

    private final OkHttpClient client;

    private final ObjectMapper objectMapper;

    private final String apiKey;

    private WebSocket webSocket;

    private Consumer<byte[]> onAudioReceived;

    private Consumer<String> onTextReceived;

    private Consumer<String> onError;

    private Runnable onConnected;

    private Runnable onClosed;
    
    public GeminiLiveClient(String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }//GeminiLiveClient


    public void connect() {
        String url = WS_URL + "?key=" + apiKey;
        Request request = new Request.Builder()
                .url(url)
                .build();
        
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("Gemini WebSocket connected");
                sendSetupMessage();
                if (onConnected != null) {
                    onConnected.run();
                }
            }//onOpen


            @Override
            public void onMessage(WebSocket webSocket, String text) {
                log.debug("Received message: {}", text);
                handleMessage(text);
            }//onMessage


            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                log.info("Gemini WebSocket closing: {} - {}", code, reason);
                webSocket.close(1000, null);
            }//onClosing


            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("Gemini WebSocket closed: {} - {}", code, reason);
                if (onClosed != null) {
                    onClosed.run();
                }
            }//onClosed


            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("Gemini WebSocket error", t);
                if (onError != null) {
                    onError.accept(t.getMessage());
                }
            }//onFailure
        });
    }//connect


    private void sendSetupMessage() {
        try {
            ObjectNode setup = objectMapper.createObjectNode();
            ObjectNode setupNode = objectMapper.createObjectNode();
            
            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("response_modalities", objectMapper.createArrayNode().add("AUDIO").add("TEXT"));
            
            setupNode.put("model", "models/gemini-2.0-flash-exp");
            setupNode.set("generation_config", generationConfig);
            
            setup.set("setup", setupNode);
            
            String json = objectMapper.writeValueAsString(setup);
            log.info("Sending Gemini setup message: {}", json);
            webSocket.send(json);
        } catch (Exception e) {
            log.error("Failed to send setup message", e);
            if (onError != null) {
                onError.accept("Failed to send setup: " + e.getMessage());
            }
        }
    }//sendSetupMessage


    public void sendAudio(byte[] pcmData) {
        try {
            ObjectNode message = objectMapper.createObjectNode();
            ObjectNode realtimeInput = objectMapper.createObjectNode();
            
            ArrayNode mediaChunks = objectMapper.createArrayNode();
            ObjectNode chunk = objectMapper.createObjectNode();
            ObjectNode inlineData = objectMapper.createObjectNode();
            
            inlineData.put("mime_type", "audio/pcm");
            inlineData.put("data", Base64.getEncoder().encodeToString(pcmData));
            
            chunk.set("inline_data", inlineData);
            mediaChunks.add(chunk);
            
            realtimeInput.set("media_chunks", mediaChunks);
            message.set("realtime_input", realtimeInput);
            
            String json = objectMapper.writeValueAsString(message);
            webSocket.send(json);
        } catch (Exception e) {
            log.error("Failed to send audio to Gemini", e);
            if (onError != null) {
                onError.accept("Failed to send audio: " + e.getMessage());
            }
        }
    }//sendAudio


    private void handleMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            
            if (root.has("serverContent")) {
                JsonNode serverContent = root.get("serverContent");
                
                if (serverContent.has("modelTurn")) {
                    JsonNode modelTurn = serverContent.get("modelTurn");
                    
                    if (modelTurn.has("parts")) {
                        JsonNode parts = modelTurn.get("parts");
                        
                        for (JsonNode part : parts) {
                            if (part.has("inlineData")) {
                                JsonNode inlineData = part.get("inlineData");
                                String base64Audio = inlineData.get("data").asText();
                                byte[] audioData = Base64.getDecoder().decode(base64Audio);

                                log.debug("Received audio data: {} bytes", audioData.length);
                                if (onAudioReceived != null) {
                                    onAudioReceived.accept(audioData);
                                }
                            }//if inlineData

                            if (part.has("text")) {
                                String text = part.get("text").asText();
                                log.debug("Received text: {}", text);
                                if (onTextReceived != null) {
                                    onTextReceived.accept(text);
                                }
                            }//if text
                        }
                    }//if parts
                }//if modelTurn
            }//if serverContent
        } catch (Exception e) {
            log.error("Failed to parse Gemini message", e);
            if (onError != null) {
                onError.accept("Failed to parse message: " + e.getMessage());
            }
        }
    }//handleMessage


    public void close() {
        if (webSocket != null) {
            log.info("Closing Gemini WebSocket connection");
            webSocket.close(1000, "Client closing");
        }
    }//close


    public void setOnAudioReceived(Consumer<byte[]> callback) {
        this.onAudioReceived = callback;
    }//setOnAudioReceived


    public void setOnTextReceived(Consumer<String> callback) {
        this.onTextReceived = callback;
    }//setOnTextReceived


    public void setOnError(Consumer<String> callback) {
        this.onError = callback;
    }//setOnError


    public void setOnConnected(Runnable callback) {
        this.onConnected = callback;
    }//setOnConnected


    public void setOnClosed(Runnable callback) {
        this.onClosed = callback;
    }//setOnClosed

}//GeminiLiveClient
// GeminiLiveClient
