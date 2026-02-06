package net.k2ai.interviewSimulator.page;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import net.k2ai.interviewSimulator.dto.InterviewSetupDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

@RequiredArgsConstructor
@Controller
@SessionAttributes("setupForm")
public class PageController {

	private static final String LAYOUT = "layouts/main";

	private final GeminiConfig geminiConfig;


	/**
	 * Root path redirects to setup wizard.
	 */
	@GetMapping("/")
	public String index() {
		return "redirect:/setup/step1";
	}// index


	/**
	 * Interview page - requires completed setup.
	 * Reads setup data from session to pass to JS for WebSocket connection.
	 * Clears setup form from session after loading (one-time use).
	 */
	@GetMapping("/interview")
	public String interview(HttpSession session, Model model, SessionStatus sessionStatus) {
		InterviewSetupDTO setupForm = (InterviewSetupDTO) session.getAttribute("setupForm");

		// Redirect to setup if not completed
		if (setupForm == null || setupForm.getCandidateName() == null || setupForm.getPosition() == null) {
			return "redirect:/setup/step1";
		}

		model.addAttribute("content", "pages/interview-standalone");
		model.addAttribute("setupForm", setupForm);
		model.addAttribute("appMode", geminiConfig.getAppMode());
		model.addAttribute("isInterviewPage", true);

		// Mark session attributes as complete - clears @SessionAttributes managed data
		// This ensures setup data is one-time use and won't persist after interview starts
		sessionStatus.setComplete();

		// Also directly remove from session to be thorough
		session.removeAttribute("setupForm");

		return LAYOUT;
	}// interview

}// PageController
