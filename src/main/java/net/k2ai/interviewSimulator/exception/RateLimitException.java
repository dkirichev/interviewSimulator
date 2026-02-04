package net.k2ai.interviewSimulator.exception;

/**
 * Exception thrown when Gemini API rate limit is exceeded
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }//RateLimitException


    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }//RateLimitException

}//RateLimitException
