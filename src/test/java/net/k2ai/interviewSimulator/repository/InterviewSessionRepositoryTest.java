package net.k2ai.interviewSimulator.repository;

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
class InterviewSessionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private InterviewSessionRepository repository;


    @Test
    void testSave_CreatesNewSession() {
        InterviewSession session = InterviewSession.builder()
                .candidateName("John Doe")
                .jobPosition("Java Developer")
                .difficulty("Standard")
                .startedAt(LocalDateTime.now())
                .transcript("")
                .build();

        InterviewSession saved = repository.save(session);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCandidateName()).isEqualTo("John Doe");
        assertThat(saved.getJobPosition()).isEqualTo("Java Developer");
        assertThat(saved.getDifficulty()).isEqualTo("Standard");
    }//testSave_CreatesNewSession


    @Test
    void testFindById_ReturnsSession() {
        InterviewSession session = InterviewSession.builder()
                .candidateName("Jane Smith")
                .jobPosition("QA Engineer")
                .difficulty("Easy")
                .startedAt(LocalDateTime.now())
                .transcript("Test transcript")
                .build();

        InterviewSession saved = repository.save(session);
        UUID id = saved.getId();

        Optional<InterviewSession> found = repository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getCandidateName()).isEqualTo("Jane Smith");
        assertThat(found.get().getTranscript()).isEqualTo("Test transcript");
    }//testFindById_ReturnsSession


    @Test
    void testFindById_ReturnsEmptyForNonExistent() {
        UUID nonExistentId = UUID.randomUUID();

        Optional<InterviewSession> found = repository.findById(nonExistentId);

        assertThat(found).isEmpty();
    }//testFindById_ReturnsEmptyForNonExistent


    @Test
    void testSave_UpdatesExistingSession() {
        InterviewSession session = InterviewSession.builder()
                .candidateName("Update Test")
                .jobPosition("Developer")
                .difficulty("Standard")
                .startedAt(LocalDateTime.now())
                .transcript("Initial")
                .build();

        InterviewSession saved = repository.save(session);
        saved.setTranscript("Updated transcript");
        saved.setEndedAt(LocalDateTime.now());

        InterviewSession updated = repository.save(saved);

        assertThat(updated.getTranscript()).isEqualTo("Updated transcript");
        assertThat(updated.getEndedAt()).isNotNull();
    }//testSave_UpdatesExistingSession

}//InterviewSessionRepositoryTest
