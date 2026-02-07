package net.k2ai.interviewSimulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.repository.InterviewSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewService {

	private final InterviewSessionRepository repository;


	@Transactional
	public UUID startSession(String name, String position, String difficulty, String language) {
		InterviewSession session = InterviewSession.builder()
				.candidateName(name)
				.jobPosition(position)
				.difficulty(difficulty)
				.language(language != null ? language : "en")
				.startedAt(LocalDateTime.now())
				.transcript("")
				.build();

		InterviewSession saved = repository.save(session);
		log.info("Started interview session: {} for candidate: {}", saved.getId(), name);
		return saved.getId();
	}//startSession


	@Transactional
	public void appendTranscript(UUID sessionId, String text) {
		InterviewSession session = repository.findById(sessionId)
				.orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

		String currentTranscript = session.getTranscript() != null ? session.getTranscript() : "";
		session.setTranscript(currentTranscript + text);

		repository.save(session);
	}//appendTranscript


	@Transactional
	public void finalizeSession(UUID sessionId) {
		InterviewSession session = repository.findById(sessionId)
				.orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

		session.setEndedAt(LocalDateTime.now());

		repository.save(session);
		log.info("Finalized interview session: {}", sessionId);
	}//finalizeSession

}//InterviewService
