package net.k2ai.interviewSimulator.service;

import net.k2ai.interviewSimulator.exception.RateLimitException;
import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(ReplaceCamelCase.class)
class RateLimitServiceTest {

    private RateLimitService rateLimitService;


    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }//setUp


    @Test
    void testCheckRateLimit_AllowsFirstRequest() {
        // Should not throw for first request
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> rateLimitService.checkRateLimit("192.168.1.1")
        );
    }//testCheckRateLimit_AllowsFirstRequest


    @Test
    void testCheckRateLimit_AllowsMultipleRequestsUnderLimit() {
        String ip = "192.168.1.2";

        // Should allow 10 requests (the limit) without throwing
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) {
                rateLimitService.checkRateLimit(ip);
            }
        });
    }//testCheckRateLimit_AllowsMultipleRequestsUnderLimit


    @Test
    void testCheckRateLimit_ThrowsWhenLimitExceeded() {
        String ip = "192.168.1.3";

        // Use up the limit
        for (int i = 0; i < 10; i++) {
            rateLimitService.checkRateLimit(ip);
        }

        // The 11th request should throw
        assertThatThrownBy(() -> rateLimitService.checkRateLimit(ip))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Too many requests");
    }//testCheckRateLimit_ThrowsWhenLimitExceeded


    @Test
    void testCheckRateLimit_DifferentIpsHaveSeparateLimits() {
        String ip1 = "192.168.1.4";
        String ip2 = "192.168.1.5";

        // Use up the limit for ip1
        for (int i = 0; i < 10; i++) {
            rateLimitService.checkRateLimit(ip1);
        }

        // ip2 should still work — ip1's limit does not affect ip2
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> rateLimitService.checkRateLimit(ip2)
        );
    }//testCheckRateLimit_DifferentIpsHaveSeparateLimits


    @Test
    void testCleanup_DoesNotThrow() {
        rateLimitService.checkRateLimit("192.168.1.6");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> rateLimitService.cleanup()
        );
    }//testCleanup_DoesNotThrow

}//RateLimitServiceTest
