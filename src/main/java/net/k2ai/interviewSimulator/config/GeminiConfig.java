package net.k2ai.interviewSimulator.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {

    @Value("${app.mode:DEV}")
    private String appMode;

    private String apiKey;

    private String liveModel = "gemini-2.0-flash-exp";

    private String gradingModel = "gemini-2.5-pro-preview-05-06";

    private String voiceName = "Aoede";


    @PostConstruct
    public void validate() {
        boolean isProdMode = "PROD".equalsIgnoreCase(appMode);
        
        if (isProdMode) {
            log.info("Running in PROD mode - users must provide their own API key");
            // API key is optional in PROD mode
        } else {
            // DEV mode - require backend API key
            if (apiKey == null || apiKey.isBlank()) {
                log.error("Gemini API key not configured! Set GEMINI_API_KEY environment variable.");
                throw new IllegalStateException("GEMINI_API_KEY environment variable required in DEV mode");
            }
            log.info("Running in DEV mode - using backend API key");
        }
        
        log.info("Gemini configuration loaded - Mode: {}, Live model: {}, Grading model: {}, Voice: {}", 
                appMode, liveModel, gradingModel, voiceName);
    }//validate


    public boolean isProdMode() {
        return "PROD".equalsIgnoreCase(appMode);
    }//isProdMode

}//GeminiConfig
