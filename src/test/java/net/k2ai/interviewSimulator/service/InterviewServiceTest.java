package net.k2ai.interviewSimulator.service;

import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.repository.InterviewSessionRepository;
import net.k2ai.interviewSimulator.testutil.AbstractIntegrationTest;
import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DisplayNameGeneration(ReplaceCamelCase.class)
class InterviewServiceTest extends AbstractIntegrationTest {

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private InterviewSessionRepository sessionRepository;


    @Test
    void testStartSession_CreatesNewSession() {
        UUID sessionId = interviewService.startSession("John Doe", "Java Developer", "Standard", "bg");

        assertThat(sessionId).isNotNull();

        Optional<InterviewSession> found = sessionRepository.findById(sessionId);
        assertThat(found).isPresent();
        assertThat(found.get().getCandidateName()).isEqualTo("John Doe");
        assertThat(found.get().getJobPosition()).isEqualTo("Java Developer");
        assertThat(found.get().getDifficulty()).isEqualTo("Standard");
        assertThat(found.get().getStartedAt()).isNotNull();
    }//testStartSession_CreatesNewSession


    @Test
    void testAppendTranscript_UpdatesExistingSession() {
        UUID sessionId = interviewService.startSession("Jane Smith", "QA Engineer", "Easy", "bg");

        interviewService.appendTranscript(sessionId, "Hello, nice to meet you. ");
        interviewService.appendTranscript(sessionId, "Tell me about yourself.");

        Optional<InterviewSession> found = sessionRepository.findById(sessionId);
        assertThat(found).isPresent();
        assertThat(found.get().getTranscript()).isEqualTo("Hello, nice to meet you. Tell me about yourself.");
    }//testAppendTranscript_UpdatesExistingSession


    @Test
    void testAppendTranscript_ThrowsForInvalidSession() {
        UUID invalidId = UUID.randomUUID();

        assertThatThrownBy(() -> interviewService.appendTranscript(invalidId, "Some text"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Session not found");
    }//testAppendTranscript_ThrowsForInvalidSession


    @Test
    void testFinalizeSession_SetsEndTime() {
        UUID sessionId = interviewService.startSession("Bob Wilson", "DevOps", "Hard", "bg");

        interviewService.finalizeSession(sessionId);

        Optional<InterviewSession> found = sessionRepository.findById(sessionId);
        assertThat(found).isPresent();
        assertThat(found.get().getEndedAt()).isNotNull();
    }//testFinalizeSession_SetsEndTime


    @Test
    void testFinalizeSession_ThrowsForInvalidSession() {
        UUID invalidId = UUID.randomUUID();

        assertThatThrownBy(() -> interviewService.finalizeSession(invalidId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Session not found");
    }//testFinalizeSession_ThrowsForInvalidSession

}//InterviewServiceTest
