package net.k2ai.interviewSimulator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for legal pages (Privacy Policy, Terms & Conditions).
 */
@Controller
@RequestMapping("/legal")
public class LegalController {

    private static final String LAYOUT = "layouts/main";

    @GetMapping("/privacy")
    public String showPrivacyPolicy(Model model) {
        model.addAttribute("content", "pages/legal/privacy");
        model.addAttribute("pageTitle", "Privacy Policy");
        model.addAttribute("showLegalLinks", true);
        return LAYOUT;
    }

    @GetMapping("/terms")
    public String showTermsAndConditions(Model model) {
        model.addAttribute("content", "pages/legal/terms");
        model.addAttribute("pageTitle", "Terms & Conditions");
        model.addAttribute("showLegalLinks", true);
        return LAYOUT;
    }
}
