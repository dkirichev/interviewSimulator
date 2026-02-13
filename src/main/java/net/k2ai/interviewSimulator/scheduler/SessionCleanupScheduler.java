package net.k2ai.interviewSimulator.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.repository.InterviewFeedbackRepository;
import net.k2ai.interviewSimulator.repository.InterviewSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class SessionCleanupScheduler {

	private final InterviewSessionRepository sessionRepository;

	private final InterviewFeedbackRepository feedbackRepository;


	/**
	 * Runs every 6 hours to delete sessions older than 2 weeks.
	 * Deletes associated feedback first (FK constraint), then sessions.
	 */
	@Scheduled(fixedRate = 6 * 60 * 60 * 1000)
	@Transactional
	public void cleanupOldSessions() {
		LocalDateTime cutoff = LocalDateTime.now().minusWeeks(2);
		List<InterviewSession> oldSessions = sessionRepository.findByStartedAtBefore(cutoff);

		if (oldSessions.isEmpty()) {
			log.info("Session cleanup: no sessions older than 2 weeks found");
			return;
		}

		int count = oldSessions.size();
		for (InterviewSession session : oldSessions) {
			feedbackRepository.deleteBySessionId(session.getId());
		}
		sessionRepository.deleteAll(oldSessions);

		log.info("Session cleanup: deleted {} sessions older than 2 weeks", count);
	}//cleanupOldSessions

}//SessionCleanupScheduler
