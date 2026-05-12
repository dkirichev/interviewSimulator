package net.k2ai.interviewSimulator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/error")
public class ErrorController {

	private static final String LAYOUT = "layouts/main";


	@GetMapping("/mobile-not-supported")
	public String showMobileNotSupported(Model model) {
		model.addAttribute("content", "pages/mobile-not-supported");
		model.addAttribute("pageTitle", "Desktop Required");
		return LAYOUT;
	}
}
