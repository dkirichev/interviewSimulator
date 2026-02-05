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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayNameGeneration(ReplaceCamelCase.class)
class SetupControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;


    // ===== Step 1 Tests =====

    @Test
    void testShowStep1_ReturnsCorrectView() throws Exception {
        mockMvc.perform(get("/setup/step1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("candidateName")));
    }//testShowStep1_ReturnsCorrectView


    @Test
    void testProcessStep1_ValidName_RedirectsToStep2() throws Exception {
        mockMvc.perform(post("/setup/step1")
                        .with(csrf())
                        .param("candidateName", "John Doe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup/step2"));
    }//testProcessStep1_ValidName_RedirectsToStep2


    @Test
    void testProcessStep1_EmptyName_ShowsErrors() throws Exception {
        mockMvc.perform(post("/setup/step1")
                        .with(csrf())
                        .param("candidateName", ""))
                .andExpect(status().isOk());
        // Stays on step1 with validation errors
    }//testProcessStep1_EmptyName_ShowsErrors


    @Test
    void testProcessStep1_NameWithNumbers_ShowsErrors() throws Exception {
        mockMvc.perform(post("/setup/step1")
                        .with(csrf())
                        .param("candidateName", "John123"))
                .andExpect(status().isOk());
        // Stays on step1 with validation errors
    }//testProcessStep1_NameWithNumbers_ShowsErrors


    @Test
    void testProcessStep1_CyrillicName_RedirectsToStep2() throws Exception {
        mockMvc.perform(post("/setup/step1")
                        .with(csrf())
                        .param("candidateName", "Иван Петров"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup/step2"));
    }//testProcessStep1_CyrillicName_RedirectsToStep2


    // ===== Step 2 Tests =====

    @Test
    void testShowStep2_WithoutStep1_RedirectsToStep1() throws Exception {
        mockMvc.perform(get("/setup/step2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup/step1"));
    }//testShowStep2_WithoutStep1_RedirectsToStep1


    @Test
    void testShowStep2_WithStep1Complete_ReturnsCorrectView() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(get("/setup/step2").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("position")));
    }//testShowStep2_WithStep1Complete_ReturnsCorrectView


    @Test
    void testProcessStep2_ValidInput_RedirectsToStep3() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(post("/setup/step2")
                        .with(csrf())
                        .session(session)
                        .param("position", "Java Developer")
                        .param("difficulty", "Standard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup/step3"));
    }//testProcessStep2_ValidInput_RedirectsToStep3


    // ===== Step 3 Tests =====

    @Test
    void testShowStep3_WithoutStep2_RedirectsToStep2() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        // Position not set
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(get("/setup/step3").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup/step2"));
    }//testShowStep3_WithoutStep2_RedirectsToStep2


    @Test
    void testShowStep3_WithStep2Complete_ReturnsCorrectView() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        setupForm.setPosition("Java Developer");
        setupForm.setDifficulty("Standard");
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(get("/setup/step3").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("voice")));
    }//testShowStep3_WithStep2Complete_ReturnsCorrectView


    @Test
    void testProcessStep3_ValidInput_RedirectsToInterview() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        setupForm.setPosition("Java Developer");
        setupForm.setDifficulty("Standard");
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(post("/setup/step3")
                        .with(csrf())
                        .session(session)
                        .param("voiceId", "Fenrir")
                        .param("language", "en"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/interview"));
    }//testProcessStep3_ValidInput_RedirectsToInterview


    @Test
    void testProcessStep3_InvalidVoice_ShowsErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        setupForm.setPosition("Java Developer");
        setupForm.setDifficulty("Standard");
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(post("/setup/step3")
                        .with(csrf())
                        .session(session)
                        .param("voiceId", "InvalidVoice")
                        .param("language", "en"))
                .andExpect(status().isOk());
        // Stays on step3 with validation errors
    }//testProcessStep3_InvalidVoice_ShowsErrors


    @Test
    void testProcessStep3_InvalidLanguage_ShowsErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        setupForm.setPosition("Java Developer");
        setupForm.setDifficulty("Standard");
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(post("/setup/step3")
                        .with(csrf())
                        .session(session)
                        .param("voiceId", "Fenrir")
                        .param("language", "fr"))
                .andExpect(status().isOk());
        // Stays on step3 with validation errors
    }//testProcessStep3_InvalidLanguage_ShowsErrors


    // ===== Clear Setup Test =====

    @Test
    void testClearSetup_RedirectsToStep1() throws Exception {
        MockHttpSession session = new MockHttpSession();
        InterviewSetupDTO setupForm = new InterviewSetupDTO();
        setupForm.setCandidateName("John Doe");
        session.setAttribute("setupForm", setupForm);

        mockMvc.perform(post("/setup/clear")
                        .with(csrf())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup/step1"));
    }//testClearSetup_RedirectsToStep1

}//SetupControllerTest
