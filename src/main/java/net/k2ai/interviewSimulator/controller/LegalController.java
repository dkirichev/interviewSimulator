package net.k2ai.interviewSimulator.controller;

import lombok.RequiredArgsConstructor;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for legal pages (Privacy Policy, Terms & Conditions).
 */
@RequiredArgsConstructor
@Controller
@RequestMapping("/legal")
public class LegalController {

	private static final String LAYOUT = "layouts/main";

	private final GeminiConfig geminiConfig;

	@GetMapping("/privacy")
	public String showPrivacyPolicy(Model model) {
		model.addAttribute("content", "pages/legal/privacy");
		model.addAttribute("pageTitle", "Privacy Policy");
		model.addAttribute("showLegalLinks", true);
		model.addAttribute("appMode", geminiConfig.getAppMode());
		return LAYOUT;
	}// showPrivacyPolicy

	@GetMapping("/terms")
	public String showTermsAndConditions(Model model) {
		model.addAttribute("content", "pages/legal/terms");
		model.addAttribute("pageTitle", "Terms & Conditions");
		model.addAttribute("showLegalLinks", true);
		model.addAttribute("appMode", geminiConfig.getAppMode());
		return LAYOUT;
	}// showTermsAndConditions
}// LegalController
