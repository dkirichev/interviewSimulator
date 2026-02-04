package net.k2ai.interviewSimulator.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.dto.InterviewSetupDTO;
import net.k2ai.interviewSimulator.service.CvProcessingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for the multi-step interview setup wizard.
 * Uses HTTP session to persist form data between steps.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/setup")
@SessionAttributes("setupForm")
public class SetupController {

    private static final String SESSION_ATTR_SETUP = "setupForm";
    private static final String LAYOUT = "layouts/main";

    private final CvProcessingService cvProcessingService;


    @ModelAttribute("setupForm")
    public InterviewSetupDTO setupForm() {
        return new InterviewSetupDTO();
    }


    // ========== STEP 1: Profile ==========

    @GetMapping({"/", "/step1"})
    public String showStep1(Model model, HttpSession session) {
        ensureSetupFormExists(session);
        model.addAttribute("content", "pages/setup/step1");
        model.addAttribute("currentStep", 1);
        return LAYOUT;
    }


    @PostMapping("/step1")
    public String processStep1(
            @ModelAttribute("setupForm") InterviewSetupDTO form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        // Validate only step 1 fields
        if (form.getCandidateName() == null || form.getCandidateName().isBlank()) {
            bindingResult.rejectValue("candidateName", "validation.name.required");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("content", "pages/setup/step1");
            model.addAttribute("currentStep", 1);
            return LAYOUT;
        }

        log.debug("Step 1 completed: candidateName={}", form.getCandidateName());
        return "redirect:/setup/step2";
    }


    // ========== STEP 2: Details ==========

    @GetMapping("/step2")
    public String showStep2(
            @ModelAttribute("setupForm") InterviewSetupDTO form,
            @RequestParam(value = "clearCv", required = false) Boolean clearCv,
            Model model,
            HttpSession session
    ) {
        // Ensure step 1 is completed
        if (form.getCandidateName() == null || form.getCandidateName().isBlank()) {
            return "redirect:/setup/step1";
        }

        // Handle CV removal
        if (Boolean.TRUE.equals(clearCv)) {
            form.clearCv();
            return "redirect:/setup/step2";
        }

        model.addAttribute("content", "pages/setup/step2");
        model.addAttribute("currentStep", 2);
        return LAYOUT;
    }


    @PostMapping("/step2")
    public String processStep2(
            @ModelAttribute("setupForm") InterviewSetupDTO form,
            BindingResult bindingResult,
            @RequestParam(value = "cvFile", required = false) MultipartFile cvFile,
            @RequestParam(value = "cvUploadOnly", required = false) Boolean cvUploadOnly,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        // Process CV if uploaded (do this first, before validation)
        boolean cvWasUploaded = false;
        if (cvFile != null && !cvFile.isEmpty()) {
            try {
                String extractedText = cvProcessingService.extractText(cvFile);
                form.setCvText(extractedText);
                form.setCvFileName(cvFile.getOriginalFilename());
                log.info("CV processed: {} ({} chars)", cvFile.getOriginalFilename(), extractedText.length());
                cvWasUploaded = true;
            } catch (IllegalArgumentException e) {
                bindingResult.rejectValue("cvFile", "validation.cv.invalid", e.getMessage());
            } catch (Exception e) {
                log.error("CV processing failed", e);
                bindingResult.rejectValue("cvFile", "validation.cv.error", "Failed to process CV");
            }
        }

        // If this was just a CV upload, stay on step2 (don't validate other fields)
        if (cvWasUploaded || Boolean.TRUE.equals(cvUploadOnly)) {
            return "redirect:/setup/step2";
        }

        // Validate position (only when moving to next step)
        if (form.getPosition() == null || form.getPosition().isBlank()) {
            bindingResult.rejectValue("position", "validation.position.required");
        } else if ("custom".equals(form.getPosition())) {
            if (form.getCustomPosition() == null || form.getCustomPosition().isBlank()) {
                bindingResult.rejectValue("customPosition", "validation.customPosition.required");
            }
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("content", "pages/setup/step2");
            model.addAttribute("currentStep", 2);
            return LAYOUT;
        }

        log.debug("Step 2 completed: position={}, difficulty={}, hasCV={}",
                form.getEffectivePosition(), form.getDifficulty(), form.getCvText() != null);
        return "redirect:/setup/step3";
    }


    // ========== STEP 3: Voice & Language ==========

    @GetMapping("/step3")
    public String showStep3(
            @ModelAttribute("setupForm") InterviewSetupDTO form,
            Model model,
            HttpSession session
    ) {
        // Ensure step 2 is completed
        if (form.getPosition() == null || form.getPosition().isBlank()) {
            return "redirect:/setup/step2";
        }

        model.addAttribute("content", "pages/setup/step3");
        model.addAttribute("currentStep", 3);
        return LAYOUT;
    }


    @PostMapping("/step3")
    public String processStep3(
            @ModelAttribute("setupForm") InterviewSetupDTO form,
            BindingResult bindingResult,
            Model model,
            HttpSession session
    ) {
        // Validate voice and language
        if (form.getVoiceId() == null || form.getVoiceId().isBlank()) {
            bindingResult.rejectValue("voiceId", "validation.voice.required");
        }
        if (form.getLanguage() == null || form.getLanguage().isBlank()) {
            bindingResult.rejectValue("language", "validation.language.required");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("content", "pages/setup/step3");
            model.addAttribute("currentStep", 3);
            return LAYOUT;
        }

        // Update interviewer names based on voice selection
        updateInterviewerNames(form);

        log.info("Setup completed: candidate={}, position={}, difficulty={}, language={}, voice={}",
                form.getCandidateName(), form.getEffectivePosition(), form.getDifficulty(),
                form.getLanguage(), form.getVoiceId());

        // Redirect to interview page (handled by PageController)
        return "redirect:/interview";
    }


    // ========== Helper Methods ==========

    private void ensureSetupFormExists(HttpSession session) {
        if (session.getAttribute(SESSION_ATTR_SETUP) == null) {
            session.setAttribute(SESSION_ATTR_SETUP, new InterviewSetupDTO());
        }
    }


    private void updateInterviewerNames(InterviewSetupDTO form) {
        switch (form.getVoiceId()) {
            case "Algieba":
                form.setInterviewerNameEN("George");
                form.setInterviewerNameBG("Георги");
                break;
            case "Kore":
                form.setInterviewerNameEN("Victoria");
                form.setInterviewerNameBG("Виктория");
                break;
            case "Fenrir":
                form.setInterviewerNameEN("Max");
                form.setInterviewerNameBG("Макс");
                break;
            case "Despina":
                form.setInterviewerNameEN("Diana");
                form.setInterviewerNameBG("Диана");
                break;
            default:
                form.setInterviewerNameEN("George");
                form.setInterviewerNameBG("Георги");
        }
    }


    /**
     * Clears the setup form from session (for starting a new interview).
     */
    @PostMapping("/clear")
    public String clearSetup(HttpSession session) {
        session.removeAttribute(SESSION_ATTR_SETUP);
        return "redirect:/setup/step1";
    }

}
