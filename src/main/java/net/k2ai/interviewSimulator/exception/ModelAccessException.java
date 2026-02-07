package net.k2ai.interviewSimulator.exception;

/**
 * Thrown when a Gemini model is not accessible (403/404).
 */
public class ModelAccessException extends RuntimeException {

	public ModelAccessException(String message) {
		super(message);
	}//ModelAccessException

}//ModelAccessException
