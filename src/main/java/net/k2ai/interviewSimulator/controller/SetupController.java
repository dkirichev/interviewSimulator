package net.k2ai.interviewSimulator.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.config.GeminiConfig;
import net.k2ai.interviewSimulator.dto.InterviewSetupDTO;
import net.k2ai.interviewSimulator.service.CvProcessingService;
import net.k2ai.interviewSimulator.service.InputSanitizerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

	private final GeminiConfig geminiConfig;
	private final CvProcessingService cvProcessingService;
	private final InputSanitizerService sanitizerService;
	private final Validator validator;


	@ModelAttribute("setupForm")
	public InterviewSetupDTO setupForm() {
		return new InterviewSetupDTO();
	}

	@ModelAttribute("appMode")
	public String appMode() {
		return geminiConfig.getAppMode();
	}

	@ModelAttribute("isSetupPage")
	public boolean isSetupPage() {
		return true;
	}


	// ========== STEP 1: Profile ==========

	@GetMapping({"/", "/step1"})
	public String showStep1(Model model, HttpSession session) {
		ensureSetupFormExists(session);
		model.addAttribute("content", "pages/setup/step1");
		model.addAttribute("currentStep", 1);
		model.addAttribute("showLegalLinks", true);
		return LAYOUT;
	}// showStep1


	@PostMapping("/step1")
	public String processStep1(
			@ModelAttribute("setupForm") InterviewSetupDTO form,
			BindingResult bindingResult,
			Model model
	) {
		// Trim and validate candidateName
		if (form.getCandidateName() != null) {
			form.setCandidateName(form.getCandidateName().trim());
		}

		// Trigger Bean Validation for candidateName field only
		validator.validate(form, bindingResult);

		// Filter to only show candidateName errors for step 1
		if (bindingResult.hasFieldErrors("candidateName")) {
			model.addAttribute("content", "pages/setup/step1");
			model.addAttribute("currentStep", 1);
			model.addAttribute("showLegalLinks", true);
			return LAYOUT;
		}

		// Double-check with sanitizer
		String sanitizedName = sanitizerService.sanitizeName(form.getCandidateName());
		if (sanitizedName == null) {
			bindingResult.rejectValue("candidateName", "validation.lettersOnly");
			model.addAttribute("content", "pages/setup/step1");
			model.addAttribute("currentStep", 1);
			model.addAttribute("showLegalLinks", true);
			return LAYOUT;
		}
		form.setCandidateName(sanitizedName);

		log.debug("Step 1 completed: candidateName={}", form.getCandidateName());
		return "redirect:/setup/step2";
	}// processStep1


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
		model.addAttribute("showLegalLinks", true);
		return LAYOUT;
	}// showStep2


	@PostMapping("/step2")
	public String processStep2(
			@ModelAttribute("setupForm") InterviewSetupDTO form,
			BindingResult bindingResult,
			@RequestParam(value = "cvFile", required = false) MultipartFile cvFile,
			@RequestParam(value = "cvUploadOnly", required = false) Boolean cvUploadOnly,
			Model model
	) {
		// Process CV if uploaded (do this first, before validation)
		boolean cvWasUploaded = false;
		if (cvFile != null && !cvFile.isEmpty()) {
			try {
				String extractedText = cvProcessingService.extractText(cvFile);
				// Sanitize extracted CV text
				String sanitizedCvText = sanitizerService.sanitizeCvText(extractedText);
				form.setCvText(sanitizedCvText);
				form.setCvFileName(cvFile.getOriginalFilename());
				log.info("CV processed: {} ({} chars)", cvFile.getOriginalFilename(), sanitizedCvText.length());
				cvWasUploaded = true;
			} catch (IllegalArgumentException e) {
				bindingResult.rejectValue("cvFile", "validation.cv.invalid");
			} catch (Exception e) {
				log.error("CV processing failed", e);
				bindingResult.rejectValue("cvFile", "validation.cv.error");
			}
		}

		// If this was just a CV upload, stay on step2 (don't validate other fields)
		if (cvWasUploaded || Boolean.TRUE.equals(cvUploadOnly)) {
			return "redirect:/setup/step2";
		}

		// Validate position
		if (form.getPosition() == null || form.getPosition().isBlank()) {
			bindingResult.rejectValue("position", "validation.position.required");
		} else if ("custom".equals(form.getPosition())) {
			// Validate custom position
			if (form.getCustomPosition() == null || form.getCustomPosition().isBlank()) {
				bindingResult.rejectValue("customPosition", "validation.customPosition.required");
			} else {
				// Trim and sanitize custom position
				form.setCustomPosition(form.getCustomPosition().trim());
				String sanitizedPosition = sanitizerService.sanitizePosition(form.getCustomPosition());
				if (sanitizedPosition == null) {
					bindingResult.rejectValue("customPosition", "validation.safeText");
				} else {
					form.setCustomPosition(sanitizedPosition);
				}
			}
		}

		// Validate difficulty
		String[] validDifficulties = {"Easy", "Standard", "Hard"};
		form.setDifficulty(sanitizerService.validateEnum(form.getDifficulty(), validDifficulties, "Easy"));

		if (bindingResult.hasErrors()) {
			model.addAttribute("content", "pages/setup/step2");
			model.addAttribute("currentStep", 2);
			model.addAttribute("showLegalLinks", true);
			return LAYOUT;
		}

		log.debug("Step 2 completed: position={}, difficulty={}, hasCV={}",
				form.getEffectivePosition(), form.getDifficulty(), form.getCvText() != null);
		return "redirect:/setup/step3";
	}// processStep2


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
		model.addAttribute("showLegalLinks", true);
		return LAYOUT;
	}// showStep3


	@PostMapping("/step3")
	public String processStep3(
			@ModelAttribute("setupForm") InterviewSetupDTO form,
			BindingResult bindingResult,
			Model model,
			HttpSession session
	) {
		// Validate and sanitize voice
		String[] validVoices = {"Algieba", "Kore", "Fenrir", "Despina"};
		String sanitizedVoice = sanitizerService.validateEnum(form.getVoiceId(), validVoices, null);
		if (sanitizedVoice == null) {
			bindingResult.rejectValue("voiceId", "validation.voice.required");
		} else {
			form.setVoiceId(sanitizedVoice);
		}

		// Validate and sanitize language
		String[] validLanguages = {"en", "bg"};
		String sanitizedLanguage = sanitizerService.validateEnum(form.getLanguage(), validLanguages, null);
		if (sanitizedLanguage == null) {
			bindingResult.rejectValue("language", "validation.language.required");
		} else {
			form.setLanguage(sanitizedLanguage);
		}

		if (bindingResult.hasErrors()) {
			model.addAttribute("content", "pages/setup/step3");
			model.addAttribute("currentStep", 3);
			model.addAttribute("showLegalLinks", true);
			return LAYOUT;
		}

		// Update interviewer names based on voice selection
		updateInterviewerNames(form);

		log.info("Setup completed: candidate={}, position={}, difficulty={}, language={}, voice={}",
				form.getCandidateName(), form.getEffectivePosition(), form.getDifficulty(),
				form.getLanguage(), form.getVoiceId());

		// Redirect to interview page (handled by PageController)
		return "redirect:/interview";
	}// processStep3


	// ========== Helper Methods ==========

	private void ensureSetupFormExists(HttpSession session) {
		if (session.getAttribute(SESSION_ATTR_SETUP) == null) {
			session.setAttribute(SESSION_ATTR_SETUP, new InterviewSetupDTO());
		}
	}// ensureSetupFormExists


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
	}// updateInterviewerNames


	/**
	 * Clears the setup form from session (for starting a new interview).
	 */
	@PostMapping("/clear")
	public String clearSetup(HttpSession session) {
		session.removeAttribute(SESSION_ATTR_SETUP);
		return "redirect:/setup/step1";
	}// clearSetup

}// SetupController
