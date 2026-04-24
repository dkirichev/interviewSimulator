package net.k2ai.interviewSimulator.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.exception.RateLimitException;
import net.k2ai.interviewSimulator.service.ClientIpResolver;
import net.k2ai.interviewSimulator.service.CvProcessingService;
import net.k2ai.interviewSimulator.service.RateLimitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cv")
public class CvController {

	private final CvProcessingService cvProcessingService;
	private final RateLimitService rateLimitService;
	private final ClientIpResolver clientIpResolver;


	@PostMapping("/upload")
	public ResponseEntity<Map<String, Object>> uploadCv(@RequestParam("file") MultipartFile file,
														HttpServletRequest request) {
		// Limit CV parsing to 5 uploads per minute per IP. Parsing PDF/DOCX is
		// heavy; unthrottled it's an easy heap/CPU DoS vector.
		String clientIp = clientIpResolver.resolve(request);
		try {
			rateLimitService.checkRateLimit("cv-upload", clientIp, 5, 60_000);
		} catch (RateLimitException e) {
			log.warn("CV upload rate limit exceeded for IP: {}", clientIp);
			return ResponseEntity.status(429).body(Map.of(
					"success", false,
					"error", "Too many uploads. Please wait a minute and try again."
			));
		}

		try {
			String extractedText = cvProcessingService.extractText(file);

			log.info("CV processed successfully, extracted {} characters", extractedText.length());

			return ResponseEntity.ok(Map.of(
					"success", true,
					"text", extractedText,
					"characterCount", extractedText.length()
			));
		} catch (IllegalArgumentException e) {
			log.warn("CV validation failed: {}", e.getMessage());
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"error", e.getMessage()
			));
		} catch (Exception e) {
			log.error("CV processing failed", e);
			// Do not propagate internal exception messages to the client.
			return ResponseEntity.internalServerError().body(Map.of(
					"success", false,
					"error", "Failed to process CV."
			));
		}
	}//uploadCv

}//CvController
