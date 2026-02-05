package net.k2ai.interviewSimulator.controller;

import net.k2ai.interviewSimulator.testutil.AbstractIntegrationTest;
import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayNameGeneration(ReplaceCamelCase.class)
class ApiKeyControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;


    @Test
    void testGetMode_ReturnsCurrentMode() throws Exception {
        mockMvc.perform(get("/api/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode", is("DEV")))
                .andExpect(jsonPath("$.requiresUserKey", is(false)));
    }//testGetMode_ReturnsCurrentMode


    @Test
    void testValidateKey_RejectsEmptyKey() throws Exception {
        mockMvc.perform(post("/api/validate-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.error", containsString("required")));
    }//testValidateKey_RejectsEmptyKey


    @Test
    void testValidateKey_RejectsNullKey() throws Exception {
        mockMvc.perform(post("/api/validate-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid", is(false)));
    }//testValidateKey_RejectsNullKey


    @Test
    void testValidateKey_RejectsInvalidFormat() throws Exception {
        mockMvc.perform(post("/api/validate-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\": \"not-a-valid-key\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.error", containsString("Invalid API key format")));
    }//testValidateKey_RejectsInvalidFormat


    @Test
    void testValidateKey_RejectsKeyWithoutAIzaPrefix() throws Exception {
        mockMvc.perform(post("/api/validate-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\": \"XXXX1234567890123456789012345678901234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.error", containsString("AIza")));
    }//testValidateKey_RejectsKeyWithoutAIzaPrefix

}//ApiKeyControllerTest
