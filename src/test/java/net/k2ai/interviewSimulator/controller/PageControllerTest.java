package net.k2ai.interviewSimulator.controller;

import net.k2ai.interviewSimulator.dto.InterviewSetupDTO;
import net.k2ai.interviewSimulator.testutil.AbstractIntegrationTest;
import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayNameGeneration(ReplaceCamelCase.class)
class PageControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;


    @Test
    void testIndex_RedirectsToSetup() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup/step1"));
    }//testIndex_RedirectsToSetup


    @Test
    void testInterview_WithoutSetup_RedirectsToSetup() throws Exception {
        mockMvc.perform(get("/interview"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup/step1"));
    }//testInterview_WithoutSetup_RedirectsToSetup


    @Test
    void testInterview_WithIncompleteSetup_RedirectsToSetup() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        // Position not set - incomplete
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(get("/interview").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup/step1"));
    }//testInterview_WithIncompleteSetup_RedirectsToSetup


    @Test
    void testInterview_WithCompleteSetup_ShowsInterviewPage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        setupForm.setPosition("Java Developer");
        setupForm.setDifficulty("Standard");
        setupForm.setLanguage("en");
        setupForm.setVoiceId("Fenrir");
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(get("/interview").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("interview")));
    }//testInterview_WithCompleteSetup_ShowsInterviewPage

}//PageControllerTest
