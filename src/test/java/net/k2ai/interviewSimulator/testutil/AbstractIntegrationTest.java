package net.k2ai.interviewSimulator.testutil;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractIntegrationTest {


    @BeforeAll
    static void startContainer() {
        SharedPostgresContainer.getInstance();

        // Set environment variables for test configuration
        System.setProperty("APP_MODE", "DEV");
        System.setProperty("GEMINI_API_KEY", "test-api-key-for-testing");
    }//startContainer


    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        SharedPostgresContainer container = SharedPostgresContainer.getInstance();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("app.mode", () -> "DEV");
        registry.add("gemini.api-key", () -> "test-api-key-for-testing");
    }//overrideProperties

}//AbstractIntegrationTest
