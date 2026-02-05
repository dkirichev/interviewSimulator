package net.k2ai.interviewSimulator.repository;

import net.k2ai.interviewSimulator.entity.InterviewFeedback;
import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.testutil.AbstractIntegrationTest;
import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DisplayNameGeneration(ReplaceCamelCase.class)
class InterviewFeedbackRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private InterviewFeedbackRepository feedbackRepository;

    @Autowired
    private InterviewSessionRepository sessionRepository;


    @Test
    void testSave_CreatesFeedback() {
        InterviewSession session = createTestSession();
        InterviewSession savedSession = sessionRepository.save(session);

        InterviewFeedback feedback = InterviewFeedback.builder()
                .session(savedSession)
                .overallScore(85)
                .communicationScore(80)
                .technicalScore(90)
                .confidenceScore(85)
                .strengths("[\"Good communication\", \"Strong technical skills\"]")
                .improvements("[\"Could improve time management\"]")
                .detailedAnalysis("Overall good candidate.")
                .verdict("HIRE")
                .build();

        InterviewFeedback saved = feedbackRepository.save(feedback);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOverallScore()).isEqualTo(85);
        assertThat(saved.getVerdict()).isEqualTo("HIRE");
        assertThat(saved.getCreatedAt()).isNotNull();
    }//testSave_CreatesFeedback


    @Test
    void testFindBySessionId_ReturnsFeedback() {
        InterviewSession session = createTestSession();
        InterviewSession savedSession = sessionRepository.save(session);

        InterviewFeedback feedback = InterviewFeedback.builder()
                .session(savedSession)
                .overallScore(75)
                .communicationScore(70)
                .technicalScore(80)
                .confidenceScore(75)
                .strengths("[\"Eager to learn\"]")
                .improvements("[\"Needs more experience\"]")
                .detailedAnalysis("Good potential.")
                .verdict("MAYBE")
                .build();

        feedbackRepository.save(feedback);

        Optional<InterviewFeedback> found = feedbackRepository.findBySessionId(savedSession.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOverallScore()).isEqualTo(75);
        assertThat(found.get().getVerdict()).isEqualTo("MAYBE");
    }//testFindBySessionId_ReturnsFeedback


    @Test
    void testFindBySessionId_ReturnsEmptyForNonExistent() {
        UUID nonExistentSessionId = UUID.randomUUID();

        Optional<InterviewFeedback> found = feedbackRepository.findBySessionId(nonExistentSessionId);

        assertThat(found).isEmpty();
    }//testFindBySessionId_ReturnsEmptyForNonExistent


    private InterviewSession createTestSession() {
        return InterviewSession.builder()
                .candidateName("Test Candidate")
                .jobPosition("Developer")
                .difficulty("Standard")
                .startedAt(LocalDateTime.now())
                .transcript("Test transcript")
                .build();
    }//createTestSession

}//InterviewFeedbackRepositoryTest
