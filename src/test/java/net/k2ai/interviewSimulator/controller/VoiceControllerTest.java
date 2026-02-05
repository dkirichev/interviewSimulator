package net.k2ai.interviewSimulator.controller;

import net.k2ai.interviewSimulator.testutil.AbstractIntegrationTest;
import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayNameGeneration(ReplaceCamelCase.class)
class VoiceControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;


    @Test
    void testGetAvailableVoices_ReturnsAllVoices() throws Exception {
        mockMvc.perform(get("/api/voices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].id", is("Algieba")))
                .andExpect(jsonPath("$[0].nameEN", is("George")))
                .andExpect(jsonPath("$[0].nameBG", is("Георги")))
                .andExpect(jsonPath("$[1].id", is("Kore")))
                .andExpect(jsonPath("$[2].id", is("Fenrir")))
                .andExpect(jsonPath("$[3].id", is("Despina")));
    }//testGetAvailableVoices_ReturnsAllVoices


    @Test
    void testGetAvailableVoices_ContainsGenderInfo() throws Exception {
        mockMvc.perform(get("/api/voices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gender", is("male")))
                .andExpect(jsonPath("$[1].gender", is("female")))
                .andExpect(jsonPath("$[2].gender", is("male")))
                .andExpect(jsonPath("$[3].gender", is("female")));
    }//testGetAvailableVoices_ContainsGenderInfo


    @Test
    void testGetVoicePreview_InvalidVoiceId_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/voices/preview/InvalidVoice/EN"))
                .andExpect(status().isBadRequest());
    }//testGetVoicePreview_InvalidVoiceId_ReturnsBadRequest


    @Test
    void testGetVoicePreview_InvalidLanguage_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/voices/preview/Fenrir/FR"))
                .andExpect(status().isBadRequest());
    }//testGetVoicePreview_InvalidLanguage_ReturnsBadRequest

}//VoiceControllerTest
