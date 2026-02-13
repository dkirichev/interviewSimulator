package net.k2ai.interviewSimulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.entity.AdminUser;
import net.k2ai.interviewSimulator.entity.InterviewFeedback;
import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.repository.AdminUserRepository;
import net.k2ai.interviewSimulator.repository.InterviewFeedbackRepository;
import net.k2ai.interviewSimulator.repository.InterviewSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class AdminServiceImpl implements AdminService {

	private final InterviewSessionRepository sessionRepository;

	private final InterviewFeedbackRepository feedbackRepository;

	private final AdminUserRepository adminUserRepository;

	private final PasswordEncoder passwordEncoder;


	@Override
	public List<InterviewSession> getRecentSessions(String position, String difficulty, String language) {
		LocalDateTime cutoff = LocalDateTime.now().minusWeeks(2);
		List<InterviewSession> sessions = sessionRepository.findByStartedAtAfterOrderByStartedAtDesc(cutoff);

		return sessions.stream()
				.filter(s -> position == null || position.isBlank() || s.getJobPosition().equalsIgnoreCase(position))
				.filter(s -> difficulty == null || difficulty.isBlank() || s.getDifficulty().equalsIgnoreCase(difficulty))
				.filter(s -> language == null || language.isBlank() || (s.getLanguage() != null && s.getLanguage().equalsIgnoreCase(language)))
				.collect(Collectors.toList());
	}//getRecentSessions


	@Override
	public Map<String, Object> getRecentSessionsPaginated(String position, String difficulty, String language, int page, int pageSize) {
		List<InterviewSession> allSessions = getRecentSessions(position, difficulty, language);
		
		int totalElements = allSessions.size();
		int totalPages = (int) Math.ceil((double) totalElements / pageSize);
		
		// Ensure page is within bounds
		if (page < 1) page = 1;
		if (page > totalPages && totalPages > 0) page = totalPages;
		
		int fromIndex = (page - 1) * pageSize;
		int toIndex = Math.min(fromIndex + pageSize, totalElements);
		
		List<InterviewSession> pageContent = fromIndex < totalElements 
			? allSessions.subList(fromIndex, toIndex) 
			: List.of();
		
		Map<String, Object> result = new HashMap<>();
		result.put("content", pageContent);
		result.put("currentPage", page);
		result.put("totalPages", totalPages);
		result.put("totalElements", totalElements);
		result.put("pageSize", pageSize);
		result.put("hasNext", page < totalPages);
		result.put("hasPrevious", page > 1);
		
		return result;
	}//getRecentSessionsPaginated


	@Override
	public Map<String, Object> getDashboardStats() {
		LocalDateTime cutoff = LocalDateTime.now().minusWeeks(2);
		List<InterviewSession> recentSessions = sessionRepository.findByStartedAtAfterOrderByStartedAtDesc(cutoff);

		Map<String, Object> stats = new HashMap<>();
		stats.put("totalSessions", recentSessions.size());

		// Sessions today
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		long sessionsToday = recentSessions.stream()
				.filter(s -> s.getStartedAt().isAfter(todayStart))
				.count();
		stats.put("sessionsToday", sessionsToday);

		// Average score from feedback
		List<InterviewFeedback> feedbacks = feedbackRepository.findBySessionStartedAtAfter(cutoff);
		double avgScore = feedbacks.stream()
				.mapToInt(InterviewFeedback::getOverallScore)
				.average()
				.orElse(0.0);
		stats.put("averageScore", Math.round(avgScore));

		// Most popular position
		String topPosition = recentSessions.stream()
				.collect(Collectors.groupingBy(InterviewSession::getJobPosition, Collectors.counting()))
				.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse("—");
		stats.put("topPosition", topPosition);

		// Completed sessions (have feedback)
		long completedSessions = feedbacks.size();
		stats.put("completedSessions", completedSessions);

		return stats;
	}//getDashboardStats


	@Override
	@Transactional
	public boolean changePassword(String currentPassword, String newPassword) {
		AdminUser admin = adminUserRepository.findByUsername("admin")
				.orElseThrow(() -> new RuntimeException("Admin user not found"));

		if (!passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
			log.warn("Admin password change failed — incorrect current password");
			return false;
		}

		admin.setPasswordHash(passwordEncoder.encode(newPassword));
		adminUserRepository.save(admin);
		log.info("Admin password changed successfully");
		return true;
	}//changePassword

}//AdminServiceImpl
