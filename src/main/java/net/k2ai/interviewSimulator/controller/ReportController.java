package net.k2ai.interviewSimulator.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.entity.InterviewFeedback;
import net.k2ai.interviewSimulator.repository.InterviewFeedbackRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Controller for server-rendered interview report pages.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/report")
public class ReportController {

	private static final String LAYOUT = "layouts/main";

	private final InterviewFeedbackRepository feedbackRepository;


	/**
	 * Display report for a specific interview session.
	 */
	@GetMapping("/{sessionId}")
	public String showReport(
			@PathVariable String sessionId,
			Model model,
			HttpSession session
	) {
		try {
			UUID uuid = UUID.fromString(sessionId);

			var feedbackOpt = feedbackRepository.findBySessionId(uuid);

			if (feedbackOpt.isEmpty()) {
				log.warn("Report not found for session: {}", sessionId);
				model.addAttribute("error", "Report not found");
				model.addAttribute("content", "pages/report-error");
				return LAYOUT;
			}

			InterviewFeedback feedback = feedbackOpt.get();

			// Parse strengths and improvements from JSON strings
			List<String> strengths = parseJsonArray(feedback.getStrengths());
			List<String> improvements = parseJsonArray(feedback.getImprovements());

			model.addAttribute("feedback", feedback);
			model.addAttribute("sessionId", sessionId.substring(0, 8));
			model.addAttribute("strengths", strengths);
			model.addAttribute("improvements", improvements);
			model.addAttribute("content", "pages/report-standalone");

			// Clear the setup form from session since interview is complete
			session.removeAttribute("setupForm");

			log.info("Displaying report for session: {}", sessionId);
			return LAYOUT;

		} catch (IllegalArgumentException e) {
			log.warn("Invalid session ID format: {}", sessionId);
			model.addAttribute("error", "Invalid session ID");
			model.addAttribute("content", "pages/report-error");
			return LAYOUT;
		}
	}


	/**
	 * Parse a JSON array string into a list of strings.
	 */
	private List<String> parseJsonArray(String jsonArray) {
		if (jsonArray == null || jsonArray.isBlank()) {
			return Collections.emptyList();
		}

		try {
			// Simple parsing for JSON arrays like ["item1", "item2"]
			String cleaned = jsonArray.trim();
			if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
				cleaned = cleaned.substring(1, cleaned.length() - 1);
			}

			if (cleaned.isBlank()) {
				return Collections.emptyList();
			}

			// Split by "," and clean up quotes
			return Arrays.stream(cleaned.split("\",\\s*\""))
					.map(s -> s.replaceAll("^\"|\"$", "").trim())
					.filter(s -> !s.isBlank())
					.toList();
		} catch (Exception e) {
			log.warn("Failed to parse JSON array: {}", jsonArray);
			return List.of(jsonArray); // Return as single item if parsing fails
		}
	}

}
