package net.k2ai.interviewSimulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;

import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class GeminiLiveClient {

    private static final String WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent";

    // Safety margin before 15-minute limit (reconnect at 14 minutes)
    private static final long SESSION_TIMEOUT_MS = 14 * 60 * 1000;

    private final OkHttpClient client;

    private final ObjectMapper objectMapper;

    private final String apiKey;

    private final String model;

    private final String voiceName;

    private WebSocket webSocket;

    private boolean isConnected = false;

    private String systemInstruction;

    // Session resumption support
    private String sessionResumptionHandle = null;

    private long sessionStartTime = 0;

    private Consumer<byte[]> onAudioReceived;

    private Consumer<String> onTextReceived;

    private Consumer<String> onInputTranscript;

    private Consumer<String> onOutputTranscript;

    private Consumer<String> onError;

    private Runnable onConnected;

    private Runnable onClosed;

    private Runnable onTurnComplete;

    private Runnable onInterrupted;

    private Consumer<String> onGoAway;

    private Runnable onSessionResumptionReady;


    public GeminiLiveClient(String apiKey, String model, String voiceName) {
        this.apiKey = apiKey;
        this.model = model;
        this.voiceName = voiceName;
        this.objectMapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }//GeminiLiveClient


    public void setSystemInstruction(String instruction) {
        this.systemInstruction = instruction;
    }//setSystemInstruction


    public void connect() {
        connect(null);
    }//connect


    public void connect(String resumptionHandle) {
        String url = WS_URL + "?key=" + apiKey;
        Request request = new Request.Builder()
                .url(url)
                .build();

        // Store resumption handle for setup message
        this.sessionResumptionHandle = resumptionHandle;
        this.sessionStartTime = System.currentTimeMillis();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("Gemini WebSocket connected" + (resumptionHandle != null ? " (resuming session)" : ""));
                isConnected = true;
                sendSetupMessage();
            }//onOpen


            @Override
            public void onMessage(WebSocket webSocket, String text) {
                log.debug("Received text message from Gemini: {}", text.length() > 200 ? text.substring(0, 200) + "..." : text);
                handleMessage(text);
            }//onMessage


            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                String text = bytes.utf8();
                log.debug("Received binary message from Gemini: {}", text.length() > 200 ? text.substring(0, 200) + "..." : text);
                handleMessage(text);
            }//onMessage


            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                log.info("Gemini WebSocket closing: {} - {}", code, reason);
                isConnected = false;
                webSocket.close(1000, null);
            }//onClosing


            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("Gemini WebSocket closed: {} - {}", code, reason);
                isConnected = false;
                if (onClosed != null) {
                    onClosed.run();
                }
            }//onClosed


            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("Gemini WebSocket error", t);
                isConnected = false;
                
                // Check for rate limit or authentication errors from response
                if (response != null) {
                    int code = response.code();
                    log.error("Gemini WebSocket failure - HTTP code: {}", code);
                    
                    if (code == 429) {
                        // Rate limit exceeded
                        if (onError != null) {
                            onError.accept("RATE_LIMIT:API rate limit exceeded");
                        }
                        return;
                    } else if (code == 401 || code == 403) {
                        // Invalid or unauthorized API key
                        if (onError != null) {
                            onError.accept("INVALID_KEY:Invalid or unauthorized API key");
                        }
                        return;
                    } else if (code == 400) {
                        // Bad request - possibly invalid key format
                        if (onError != null) {
                            onError.accept("INVALID_KEY:Invalid API key");
                        }
                        return;
                    }
                }
                
                if (onError != null) {
                    onError.accept(t.getMessage());
                }
            }//onFailure
        });
    }//connect


    private void sendSetupMessage() {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode setup = objectMapper.createObjectNode();

            // Model configuration
            setup.put("model", "models/" + model);

            // Generation config - Audio only response (camelCase per API docs)
            ObjectNode generationConfig = objectMapper.createObjectNode();
            ArrayNode modalities = objectMapper.createArrayNode();
            modalities.add("AUDIO");
            generationConfig.set("responseModalities", modalities);

            // Voice configuration (camelCase)
            ObjectNode speechConfig = objectMapper.createObjectNode();
            ObjectNode voiceConfig = objectMapper.createObjectNode();
            ObjectNode prebuiltVoiceConfig = objectMapper.createObjectNode();
            prebuiltVoiceConfig.put("voiceName", voiceName);
            voiceConfig.set("prebuiltVoiceConfig", prebuiltVoiceConfig);
            speechConfig.set("voiceConfig", voiceConfig);
            generationConfig.set("speechConfig", speechConfig);

            setup.set("generationConfig", generationConfig);

            // Context window compression - enables UNLIMITED session length
            // Without this, audio-only sessions are limited to 15 minutes
            ObjectNode contextWindowCompression = objectMapper.createObjectNode();
            ObjectNode slidingWindow = objectMapper.createObjectNode();
            contextWindowCompression.set("slidingWindow", slidingWindow);
            setup.set("contextWindowCompression", contextWindowCompression);

            // Session resumption - allows resuming if connection drops
            ObjectNode sessionResumption = objectMapper.createObjectNode();
            if (sessionResumptionHandle != null) {
                // Resume existing session
                sessionResumption.put("handle", sessionResumptionHandle);
                log.info("Resuming session with handle: {}...", 
                        sessionResumptionHandle.substring(0, Math.min(20, sessionResumptionHandle.length())));
            }
            setup.set("sessionResumption", sessionResumption);

            // System instruction (camelCase) - only for new sessions, not resumptions
            if (sessionResumptionHandle == null && systemInstruction != null && !systemInstruction.isBlank()) {
                ObjectNode sysInstr = objectMapper.createObjectNode();
                ArrayNode parts = objectMapper.createArrayNode();
                ObjectNode textPart = objectMapper.createObjectNode();
                textPart.put("text", systemInstruction);
                parts.add(textPart);
                sysInstr.set("parts", parts);
                setup.set("systemInstruction", sysInstr);
            }

            // Enable transcription for building transcript (camelCase)
            setup.set("inputAudioTranscription", objectMapper.createObjectNode());
            setup.set("outputAudioTranscription", objectMapper.createObjectNode());

            root.set("setup", setup);

            String json = objectMapper.writeValueAsString(root);
            log.info("Sending Gemini setup message (compression: enabled, resumption: {})", 
                    sessionResumptionHandle != null ? "resuming" : "new");
            log.debug("Setup payload: {}", json);
            webSocket.send(json);
        } catch (Exception e) {
            log.error("Failed to send setup message", e);
            if (onError != null) {
                onError.accept("Failed to send setup: " + e.getMessage());
            }
        }
    }//sendSetupMessage


    public void sendAudio(byte[] pcmData) {
        if (!isConnected || webSocket == null) {
            log.warn("Cannot send audio - not connected");
            return;
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode realtimeInput = objectMapper.createObjectNode();
            ObjectNode audio = objectMapper.createObjectNode();

            // Use camelCase per API docs
            audio.put("data", Base64.getEncoder().encodeToString(pcmData));
            audio.put("mimeType", "audio/pcm;rate=16000");

            realtimeInput.set("audio", audio);
            root.set("realtimeInput", realtimeInput);

            String json = objectMapper.writeValueAsString(root);
            webSocket.send(json);
        } catch (Exception e) {
            log.error("Failed to send audio to Gemini", e);
            if (onError != null) {
                onError.accept("Failed to send audio: " + e.getMessage());
            }
        }
    }//sendAudio


    public void sendText(String text) {
        if (!isConnected || webSocket == null) {
            log.warn("Cannot send text - not connected");
            return;
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode clientContent = objectMapper.createObjectNode();

            ArrayNode turns = objectMapper.createArrayNode();
            ObjectNode turn = objectMapper.createObjectNode();
            turn.put("role", "user");

            ArrayNode parts = objectMapper.createArrayNode();
            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("text", text);
            parts.add(textPart);

            turn.set("parts", parts);
            turns.add(turn);

            clientContent.set("turns", turns);
            clientContent.put("turnComplete", true);

            root.set("clientContent", clientContent);

            String json = objectMapper.writeValueAsString(root);
            log.debug("Sending text to Gemini: {}", text);
            webSocket.send(json);
        } catch (Exception e) {
            log.error("Failed to send text to Gemini", e);
            if (onError != null) {
                onError.accept("Failed to send text: " + e.getMessage());
            }
        }
    }//sendText


    public void sendAudioStreamEnd() {
        if (!isConnected || webSocket == null) {
            return;
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode realtimeInput = objectMapper.createObjectNode();
            realtimeInput.put("audioStreamEnd", true);
            root.set("realtimeInput", realtimeInput);

            String json = objectMapper.writeValueAsString(root);
            webSocket.send(json);
            log.debug("Sent audio stream end signal");
        } catch (Exception e) {
            log.error("Failed to send audio stream end", e);
        }
    }//sendAudioStreamEnd


    private void handleMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Handle setup complete
            if (root.has("setupComplete")) {
                log.info("Gemini setup complete");
                if (onConnected != null) {
                    onConnected.run();
                }
                return;
            }

            // Handle session resumption update - store token for reconnection
            if (root.has("sessionResumptionUpdate")) {
                JsonNode update = root.get("sessionResumptionUpdate");
                if (update.has("resumable") && update.get("resumable").asBoolean() && update.has("newHandle")) {
                    String newHandle = update.get("newHandle").asText();
                    this.sessionResumptionHandle = newHandle;
                    log.debug("Received session resumption handle: {}...", 
                            newHandle.substring(0, Math.min(20, newHandle.length())));
                    if (onSessionResumptionReady != null) {
                        onSessionResumptionReady.run();
                    }
                }
            }

            // Handle server content
            if (root.has("serverContent")) {
                JsonNode serverContent = root.get("serverContent");

                // Check for interruption
                if (serverContent.has("interrupted") && serverContent.get("interrupted").asBoolean()) {
                    log.debug("Generation was interrupted");
                    if (onInterrupted != null) {
                        onInterrupted.run();
                    }
                    return;
                }

                // Check for turn complete
                if (serverContent.has("turnComplete") && serverContent.get("turnComplete").asBoolean()) {
                    log.debug("Model turn complete");
                    if (onTurnComplete != null) {
                        onTurnComplete.run();
                    }
                }

                // Handle input transcription (user's speech)
                if (serverContent.has("inputTranscription")) {
                    JsonNode inputTranscription = serverContent.get("inputTranscription");
                    if (inputTranscription.has("text")) {
                        String transcript = inputTranscription.get("text").asText();
                        log.debug("Input transcription: {}", transcript);
                        if (onInputTranscript != null) {
                            onInputTranscript.accept(transcript);
                        }
                    }
                }

                // Handle output transcription (AI's speech)
                if (serverContent.has("outputTranscription")) {
                    JsonNode outputTranscription = serverContent.get("outputTranscription");
                    if (outputTranscription.has("text")) {
                        String transcript = outputTranscription.get("text").asText();
                        log.debug("Output transcription: {}", transcript);
                        if (onOutputTranscript != null) {
                            onOutputTranscript.accept(transcript);
                        }
                    }
                }

                // Handle model turn (audio/text content)
                if (serverContent.has("modelTurn")) {
                    JsonNode modelTurn = serverContent.get("modelTurn");

                    if (modelTurn.has("parts")) {
                        JsonNode parts = modelTurn.get("parts");

                        for (JsonNode part : parts) {
                            // Handle audio data
                            if (part.has("inlineData")) {
                                JsonNode inlineData = part.get("inlineData");
                                if (inlineData.has("data")) {
                                    String base64Audio = inlineData.get("data").asText();
                                    byte[] audioData = Base64.getDecoder().decode(base64Audio);
                                    log.debug("Received audio data: {} bytes", audioData.length);
                                    if (onAudioReceived != null) {
                                        onAudioReceived.accept(audioData);
                                    }
                                }
                            }

                            // Handle text content
                            if (part.has("text")) {
                                String text = part.get("text").asText();
                                log.debug("Received text: {}", text);
                                if (onTextReceived != null) {
                                    onTextReceived.accept(text);
                                }
                            }
                        }
                    }
                }
            }

            // Handle go away (connection will close soon) - trigger reconnection
            if (root.has("goAway")) {
                JsonNode goAway = root.get("goAway");
                String timeLeft = goAway.has("timeLeft") ? goAway.get("timeLeft").asText() : "unknown";
                log.warn("Gemini GoAway received! Connection will close. Time left: {}", timeLeft);
                if (onGoAway != null) {
                    onGoAway.accept(timeLeft);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse Gemini message: {}", json, e);
            if (onError != null) {
                onError.accept("Failed to parse message: " + e.getMessage());
            }
        }
    }//handleMessage


    public void close() {
        if (webSocket != null) {
            log.info("Closing Gemini WebSocket connection");
            isConnected = false;
            webSocket.close(1000, "Client closing");
        }
    }//close


    public boolean isConnected() {
        return isConnected;
    }//isConnected


    // Callback setters
    public void setOnAudioReceived(Consumer<byte[]> callback) {
        this.onAudioReceived = callback;
    }//setOnAudioReceived


    public void setOnTextReceived(Consumer<String> callback) {
        this.onTextReceived = callback;
    }//setOnTextReceived


    public void setOnInputTranscript(Consumer<String> callback) {
        this.onInputTranscript = callback;
    }//setOnInputTranscript


    public void setOnOutputTranscript(Consumer<String> callback) {
        this.onOutputTranscript = callback;
    }//setOnOutputTranscript


    public void setOnError(Consumer<String> callback) {
        this.onError = callback;
    }//setOnError


    public void setOnConnected(Runnable callback) {
        this.onConnected = callback;
    }//setOnConnected


    public void setOnClosed(Runnable callback) {
        this.onClosed = callback;
    }//setOnClosed


    public void setOnTurnComplete(Runnable callback) {
        this.onTurnComplete = callback;
    }//setOnTurnComplete


    public void setOnInterrupted(Runnable callback) {
        this.onInterrupted = callback;
    }//setOnInterrupted


    public void setOnGoAway(Consumer<String> callback) {
        this.onGoAway = callback;
    }//setOnGoAway


    public void setOnSessionResumptionReady(Runnable callback) {
        this.onSessionResumptionReady = callback;
    }//setOnSessionResumptionReady


    public String getSessionResumptionHandle() {
        return sessionResumptionHandle;
    }//getSessionResumptionHandle


    public long getSessionStartTime() {
        return sessionStartTime;
    }//getSessionStartTime


    public boolean isApproachingTimeout() {
        if (sessionStartTime == 0) return false;
        return (System.currentTimeMillis() - sessionStartTime) > SESSION_TIMEOUT_MS;
    }//isApproachingTimeout

}//GeminiLiveClient
