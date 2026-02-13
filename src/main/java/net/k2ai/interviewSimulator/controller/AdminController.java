package net.k2ai.interviewSimulator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.entity.InterviewFeedback;
import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.repository.InterviewFeedbackRepository;
import net.k2ai.interviewSimulator.service.AdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
public class AdminController {

	private static final String LAYOUT = "layouts/main";

	private final AdminService adminService;

	private final InterviewFeedbackRepository feedbackRepository;


	@GetMapping("/login")
	public String loginPage(
			@RequestParam(value = "error", required = false) String error,
			@RequestParam(value = "logout", required = false) String logout,
			Model model,
			jakarta.servlet.http.HttpServletRequest request
	) {
		// Ensure session exists before template rendering (needed for CSRF token)
		request.getSession(true);
		
		if (error != null) {
			model.addAttribute("loginError", true);
		}
		if (logout != null) {
			model.addAttribute("logoutSuccess", true);
		}
		model.addAttribute("content", "pages/admin/login");
		model.addAttribute("isAdminPage", true);
		return LAYOUT;
	}//loginPage


	@GetMapping("")
	public String adminRoot() {
		return "redirect:/admin/dashboard";
	}//adminRoot


	@GetMapping("/dashboard")
	public String dashboard(
			@RequestParam(value = "position", required = false) String position,
			@RequestParam(value = "difficulty", required = false) String difficulty,
			@RequestParam(value = "language", required = false) String language,
			@RequestParam(value = "page", required = false, defaultValue = "1") int page,
			Model model
	) {
		Map<String, Object> pageData = adminService.getRecentSessionsPaginated(position, difficulty, language, page, 15);
		
		@SuppressWarnings("unchecked")
		List<InterviewSession> sessions = (List<InterviewSession>) pageData.get("content");
		
		Map<String, Object> stats = adminService.getDashboardStats();

		// Override totalSessions with actual total from pagination (respects filters)
		stats.put("totalSessions", pageData.get("totalElements"));

		// Build feedback lookup map (sessionId -> feedback)
		Map<UUID, InterviewFeedback> feedbackMap = sessions.stream()
				.map(s -> feedbackRepository.findBySessionId(s.getId()).orElse(null))
				.filter(f -> f != null)
				.collect(Collectors.toMap(f -> f.getSession().getId(), f -> f));

		// Calculate durations (in minutes)
		Map<UUID, String> durationMap = sessions.stream()
				.filter(s -> s.getEndedAt() != null)
				.collect(Collectors.toMap(
						InterviewSession::getId,
						s -> {
							Duration d = Duration.between(s.getStartedAt(), s.getEndedAt());
							long mins = d.toMinutes();
							long secs = d.toSecondsPart();
							return mins + "m " + secs + "s";
						}
				));

		// Collect unique positions from ALL recent sessions (not filtered) for dropdown
		List<String> allPositions = adminService.getRecentSessions(null, null, null).stream()
				.map(InterviewSession::getJobPosition)
				.distinct()
				.sorted()
				.collect(Collectors.toList());

		model.addAttribute("sessions", sessions);
		model.addAttribute("feedbackMap", feedbackMap);
		model.addAttribute("durationMap", durationMap);
		model.addAttribute("stats", stats);
		model.addAttribute("positions", allPositions);
		model.addAttribute("filterPosition", position);
		model.addAttribute("filterDifficulty", difficulty);
		model.addAttribute("filterLanguage", language);
		model.addAttribute("currentPage", pageData.get("currentPage"));
		model.addAttribute("totalPages", pageData.get("totalPages"));
		model.addAttribute("hasNext", pageData.get("hasNext"));
		model.addAttribute("hasPrevious", pageData.get("hasPrevious"));
		model.addAttribute("content", "pages/admin/dashboard");
		model.addAttribute("isAdminPage", true);

		return LAYOUT;
	}//dashboard


	@PostMapping("/change-password")
	public String changePassword(
			@RequestParam("currentPassword") String currentPassword,
			@RequestParam("newPassword") String newPassword,
			@RequestParam("confirmPassword") String confirmPassword,
			RedirectAttributes redirectAttributes
	) {
		if (newPassword == null || newPassword.length() < 8) {
			redirectAttributes.addFlashAttribute("passwordError", "admin.password.tooShort");
			return "redirect:/admin/dashboard";
		}

		if (!newPassword.equals(confirmPassword)) {
			redirectAttributes.addFlashAttribute("passwordError", "admin.password.mismatch");
			return "redirect:/admin/dashboard";
		}

		boolean success = adminService.changePassword(currentPassword, newPassword);
		if (success) {
			redirectAttributes.addFlashAttribute("passwordSuccess", true);
		} else {
			redirectAttributes.addFlashAttribute("passwordError", "admin.password.wrongCurrent");
		}

		return "redirect:/admin/dashboard";
	}//changePassword

}//AdminController
