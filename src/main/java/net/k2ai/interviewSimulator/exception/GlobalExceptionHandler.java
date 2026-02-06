package net.k2ai.interviewSimulator.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Global exception handler for the Interview Simulator.
 * Provides consistent error handling for both MVC and REST endpoints.
 */
@Slf4j
@RequiredArgsConstructor
@ControllerAdvice
public class GlobalExceptionHandler {

	private final MessageSource messageSource;


	/**
	 * Handles validation errors from @Valid on REST endpoints.
	 * Returns JSON with field-level errors.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationException(
			MethodArgumentNotValidException ex,
			HttpServletRequest request,
			Locale locale) {

		log.warn("Validation failed for request to {}: {}", request.getRequestURI(), ex.getMessage());

		Map<String, String> fieldErrors = new HashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			String message = messageSource.getMessage(error, locale);
			fieldErrors.put(error.getField(), message);
		}

		return ResponseEntity.badRequest().body(Map.of(
				"success", false,
				"error", "Validation failed",
				"fieldErrors", fieldErrors
		));
	}


	/**
	 * Handles form binding errors.
	 * Returns JSON for API requests, or renders error for MVC.
	 */
	@ExceptionHandler(BindException.class)
	public Object handleBindException(
			BindException ex,
			HttpServletRequest request,
			Model model,
			Locale locale) {

		log.warn("Binding failed for request to {}: {}", request.getRequestURI(), ex.getMessage());

		// Check if this is an API request
		if (isApiRequest(request)) {
			Map<String, String> fieldErrors = new HashMap<>();
			for (FieldError error : ex.getBindingResult().getFieldErrors()) {
				String message = messageSource.getMessage(error, locale);
				fieldErrors.put(error.getField(), message);
			}

			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"error", "Validation failed",
					"fieldErrors", fieldErrors
			));
		}

		// For MVC requests, let the controller handle it normally
		// This exception shouldn't reach here for properly configured controllers
		return renderErrorPage(model, "400", "Bad Request",
				"The form data was invalid. Please check your input.");
	}


	/**
	 * Handles file upload size exceeded errors.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public Object handleMaxUploadSizeExceeded(
			MaxUploadSizeExceededException ex,
			HttpServletRequest request,
			Model model,
			Locale locale) {

		log.warn("File size exceeded for request to {}", request.getRequestURI());

		String message = messageSource.getMessage("validation.cv.tooLarge", null,
				"File size must be less than 10MB", locale);

		if (isApiRequest(request)) {
			return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
					"success", false,
					"error", message
			));
		}

		return renderErrorPage(model, "413", "File Too Large", message);
	}


	/**
	 * Handles rate limit exceeded errors.
	 */
	@ExceptionHandler(RateLimitException.class)
	public ResponseEntity<Map<String, Object>> handleRateLimitException(RateLimitException ex) {
		log.warn("Rate limit exceeded: {}", ex.getMessage());

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
				"success", false,
				"error", ex.getMessage(),
				"rateLimited", true
		));
	}


	/**
	 * Handles missing static resources (favicon.ico, .well-known, etc).
	 * Silently returns 404 without logging errors for common browser requests.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
		// Don't log common browser requests
		return ResponseEntity.notFound().build();
	}


	/**
	 * Handles IllegalArgumentException (often from validation).
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public Object handleIllegalArgumentException(
			IllegalArgumentException ex,
			HttpServletRequest request,
			Model model) {

		log.warn("Illegal argument for request to {}: {}", request.getRequestURI(), ex.getMessage());

		if (isApiRequest(request)) {
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"error", ex.getMessage()
			));
		}

		return renderErrorPage(model, "400", "Bad Request", ex.getMessage());
	}


	/**
	 * Catches all unhandled exceptions.
	 * Logs the error and returns a generic error response.
	 */
	@ExceptionHandler(Exception.class)
	public Object handleGenericException(
			Exception ex,
			HttpServletRequest request,
			Model model) {

		log.error("Unhandled exception for request to {}", request.getRequestURI(), ex);

		if (isApiRequest(request)) {
			return ResponseEntity.internalServerError().body(Map.of(
					"success", false,
					"error", "An unexpected error occurred. Please try again."
			));
		}

		return renderErrorPage(model, "500", "Internal Server Error",
				"Something went wrong. Please try again later.");
	}


	private boolean isApiRequest(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String accept = request.getHeader("Accept");
		return uri.contains("/api/") ||
				(accept != null && accept.contains("application/json"));
	}


	private String renderErrorPage(Model model, String code, String title, String message) {
		model.addAttribute("errorCode", code);
		model.addAttribute("errorTitle", title);
		model.addAttribute("errorMessage", message);
		model.addAttribute("content", "pages/error");
		return "layouts/main";
	}

}
