package net.k2ai.interviewSimulator.service;

import lombok.RequiredArgsConstructor;
import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.repository.InterviewSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewService {
    
    private final InterviewSessionRepository repository;
    
    @Transactional
    public UUID startSession(String name, String position, String difficulty) {
        InterviewSession session = InterviewSession.builder()
                .candidateName(name)
                .jobPosition(position)
                .difficulty(difficulty)
                .startedAt(LocalDateTime.now())
                .transcript("")
                .build();
        
        InterviewSession saved = repository.save(session);
        return saved.getId();
    }
    
    @Transactional
    public void appendTranscript(UUID sessionId, String text) {
        InterviewSession session = repository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
        
        String currentTranscript = session.getTranscript() != null ? session.getTranscript() : "";
        session.setTranscript(currentTranscript + text);
        
        repository.save(session);
    }
    
    @Transactional
    public void finalizeSession(UUID sessionId) {
        InterviewSession session = repository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
        
        session.setEndedAt(LocalDateTime.now());
        
        // TODO: Add grading logic here later
        
        repository.save(session);
    }
}// InterviewService
