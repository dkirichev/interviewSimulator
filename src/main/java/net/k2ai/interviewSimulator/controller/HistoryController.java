package net.k2ai.interviewSimulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.repository.InterviewSessionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
public class HistoryController {

	private static final String LAYOUT = "layouts/main";

	private final InterviewSessionRepository sessionRepository;
	private final GeminiConfig geminiConfig;

	@ModelAttribute("appMode")
	public String appMode() {
		return geminiConfig.getAppMode();
	}// appMode


	@GetMapping("/history")
	public String showHistory(
			@RequestParam(value = "token", required = false) String userToken,
			Model model
	) {
		List<InterviewSession> sessions = List.of();

		if (userToken != null && !userToken.isBlank() && userToken.length() <= 64) {
			sessions = sessionRepository.findByUserTokenOrderByStartedAtDesc(userToken);
		}

		model.addAttribute("sessions", sessions);
		model.addAttribute("content", "pages/history");
		return LAYOUT;
	}// showHistory

}// HistoryController
