package net.k2ai.interviewSimulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.service.CvProcessingService;
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


	@PostMapping("/upload")
	public ResponseEntity<Map<String, Object>> uploadCv(@RequestParam("file") MultipartFile file) {
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
			return ResponseEntity.internalServerError().body(Map.of(
					"success", false,
					"error", "Failed to process CV: " + e.getMessage()
			));
		}
	}//uploadCv

}//CvController
