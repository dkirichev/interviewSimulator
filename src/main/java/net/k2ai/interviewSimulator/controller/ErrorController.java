package net.k2ai.interviewSimulator.controller;

import lombok.RequiredArgsConstructor;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequiredArgsConstructor
@Controller
@RequestMapping("/error")
public class ErrorController {

	private static final String LAYOUT = "layouts/main";

	private final GeminiConfig geminiConfig;

	@GetMapping("/mobile-not-supported")
	public String showMobileNotSupported(Model model) {
		model.addAttribute("content", "pages/mobile-not-supported");
		model.addAttribute("pageTitle", "Desktop Required");
		model.addAttribute("appMode", geminiConfig.getAppMode());
		return LAYOUT;
	}
}
