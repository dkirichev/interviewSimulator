package net.k2ai.interviewSimulator.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {

    private String apiKey;

    private String liveModel = "gemini-2.0-flash-exp";

    private String gradingModel = "gemini-2.5-pro-preview-05-06";


    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Gemini API key not configured! Set GEMINI_API_KEY environment variable.");
            throw new IllegalStateException("GEMINI_API_KEY environment variable required");
        }
        log.info("Gemini configuration loaded - Live model: {}, Grading model: {}", liveModel, gradingModel);
    }//validate

}//GeminiConfig
