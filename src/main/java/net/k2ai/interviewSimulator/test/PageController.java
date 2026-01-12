package net.k2ai.interviewSimulator.test;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {


	@GetMapping("/")
	public String index(Model model) {
		model.addAttribute("content", "pages/index");
		model.addAttribute("pageTitle", "Home");
		return "layouts/main";
	}

}// PageController
