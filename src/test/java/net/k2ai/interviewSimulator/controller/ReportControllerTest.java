package net.k2ai.interviewSimulator.controller;

import net.k2ai.interviewSimulator.entity.InterviewFeedback;
import net.k2ai.interviewSimulator.entity.InterviewSession;
import net.k2ai.interviewSimulator.repository.InterviewFeedbackRepository;
import net.k2ai.interviewSimulator.repository.InterviewSessionRepository;
import net.k2ai.interviewSimulator.testutil.AbstractIntegrationTest;
import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayNameGeneration(ReplaceCamelCase.class)
class ReportControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewSessionRepository sessionRepository;

    @Autowired
    private InterviewFeedbackRepository feedbackRepository;


    @Test
    void testShowReport_InvalidUUID_ShowsError() throws Exception {
        mockMvc.perform(get("/report/not-a-valid-uuid"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid session ID")));
    }//testShowReport_InvalidUUID_ShowsError


    @Test
    void testShowReport_NotFound_ShowsError() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/report/" + nonExistentId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Report not found")));
    }//testShowReport_NotFound_ShowsError


    @Test
    void testShowReport_ValidSession_ShowsReport() throws Exception {
        // Create session
        InterviewSession session = InterviewSession.builder()
                .candidateName("Test Candidate")
                .jobPosition("Java Developer")
                .difficulty("Standard")
                .startedAt(LocalDateTime.now())
                .endedAt(LocalDateTime.now())
                .transcript("Test transcript")
                .build();
        InterviewSession savedSession = sessionRepository.save(session);

        // Create feedback
        InterviewFeedback feedback = InterviewFeedback.builder()
                .session(savedSession)
                .overallScore(85)
                .communicationScore(80)
                .technicalScore(90)
                .confidenceScore(85)
                .strengths("[\"Good communication\"]")
                .improvements("[\"Could improve time management\"]")
                .detailedAnalysis("Good candidate overall.")
                .verdict("HIRE")
                .build();
        feedbackRepository.save(feedback);

        mockMvc.perform(get("/report/" + savedSession.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("85")))
                .andExpect(content().string(containsString("Good communication")));
    }//testShowReport_ValidSession_ShowsReport


    @Test
    void testShowReport_DisplaysScores() throws Exception {
        InterviewSession session = InterviewSession.builder()
                .candidateName("Score Test")
                .jobPosition("QA Engineer")
                .difficulty("Easy")
                .startedAt(LocalDateTime.now())
                .endedAt(LocalDateTime.now())
                .transcript("Test")
                .build();
        InterviewSession savedSession = sessionRepository.save(session);

        InterviewFeedback feedback = InterviewFeedback.builder()
                .session(savedSession)
                .overallScore(75)
                .communicationScore(70)
                .technicalScore(80)
                .confidenceScore(75)
                .strengths("[\"Attention to detail\"]")
                .improvements("[\"Technical depth\"]")
                .detailedAnalysis("Promising candidate.")
                .verdict("MAYBE")
                .build();
        feedbackRepository.save(feedback);

        mockMvc.perform(get("/report/" + savedSession.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("75")))
                .andExpect(content().string(containsString("Attention to detail")));
    }//testShowReport_DisplaysScores

}//ReportControllerTest
