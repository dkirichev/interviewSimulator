package net.k2ai.interviewSimulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiKeyController {

    private static final String GEMINI_MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models?key=";
    
    // Gemini API keys start with "AIza" and are about 39 characters
    private static final Pattern API_KEY_PATTERN = Pattern.compile("^AIza[A-Za-z0-9_-]{35,}$");

    private final GeminiConfig geminiConfig;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();


    /**
     * Returns the current application mode (DEV or PROD)
     */
    @GetMapping("/mode")
    public ResponseEntity<Map<String, Object>> getMode() {
        return ResponseEntity.ok(Map.of(
                "mode", geminiConfig.getAppMode(),
                "requiresUserKey", geminiConfig.isProdMode()
        ));
    }//getMode


    /**
     * Validates a user-provided Gemini API key
     */
    @PostMapping("/validate-key")
    public ResponseEntity<Map<String, Object>> validateApiKey(@RequestBody Map<String, String> payload) {
        String apiKey = payload.get("apiKey");

        // Basic validation - check format before making network request
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "error", "API key is required"
            ));
        }

        // Sanitize - remove whitespace
        apiKey = apiKey.trim();

        // Validate format to prevent injection
        if (!API_KEY_PATTERN.matcher(apiKey).matches()) {
            log.warn("Invalid API key format attempted");
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "error", "Invalid API key format. Gemini API keys start with 'AIza' and are about 39 characters."
            ));
        }

        // Validate by calling Gemini API
        try {
            Request request = new Request.Builder()
                    .url(GEMINI_MODELS_URL + apiKey)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("API key validated successfully");
                    return ResponseEntity.ok(Map.of(
                            "valid", true,
                            "message", "API key is valid"
                    ));
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.warn("API key validation failed: {} - {}", response.code(), errorBody);

                    // Check for specific error types
                    if (response.code() == 400 || response.code() == 403) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "valid", false,
                                "error", "Invalid API key. Please check that you copied it correctly."
                        ));
                    } else if (response.code() == 429) {
                        return ResponseEntity.status(429).body(Map.of(
                                "valid", false,
                                "error", "This API key has exceeded its quota. Please create a new key with a different Google account.",
                                "rateLimited", true
                        ));
                    } else {
                        return ResponseEntity.badRequest().body(Map.of(
                                "valid", false,
                                "error", "Unable to validate API key. Please try again."
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error validating API key", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "valid", false,
                    "error", "Server error while validating API key. Please try again."
            ));
        }
    }//validateApiKey

}//ApiKeyController
