package net.k2ai.interviewSimulator.service;

import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(ReplaceCamelCase.class)
class InterviewPromptServiceTest {

    private InterviewPromptService promptService;


    @BeforeEach
    void setUp() {
        promptService = new InterviewPromptService();
    }//setUp


    // ===== Prompt Generation Tests =====

    @Test
    void testGenerateInterviewerPrompt_EnglishContainsPositionAndDifficulty() {
        String prompt = promptService.generateInterviewerPrompt("Java Developer", "Standard", "en");

        assertThat(prompt).contains("Java Developer");
        assertThat(prompt).contains("Standard");
        assertThat(prompt).contains("interview");
    }//testGenerateInterviewerPrompt_EnglishContainsPositionAndDifficulty


    @Test
    void testGenerateInterviewerPrompt_BulgarianContainsPositionAndDifficulty() {
        String prompt = promptService.generateInterviewerPrompt("Java Developer", "Standard", "bg");

        assertThat(prompt).contains("Java Developer");
        assertThat(prompt).contains("Standard");
        assertThat(prompt).contains("интервю");
    }//testGenerateInterviewerPrompt_BulgarianContainsPositionAndDifficulty


    @Test
    void testGenerateInterviewerPrompt_WithCvText_IncludesCvSection() {
        String cvText = "Experienced Java developer with 5 years experience";
        String prompt = promptService.generateInterviewerPrompt("Java Developer", "Standard", "en", cvText);

        assertThat(prompt).contains("CV");
        assertThat(prompt).contains(cvText);
    }//testGenerateInterviewerPrompt_WithCvText_IncludesCvSection


    @Test
    void testGenerateInterviewerPrompt_WithInterviewerNames() {
        String prompt = promptService.generateInterviewerPrompt(
                "Java Developer", "Standard", "en", null, "George", "Георги");

        assertThat(prompt).contains("George");
    }//testGenerateInterviewerPrompt_WithInterviewerNames


    @Test
    void testGenerateInterviewerPrompt_EasyDifficulty_ContainsFriendlyBehavior() {
        String prompt = promptService.generateInterviewerPrompt("Developer", "Easy", "en");

        assertThat(prompt.toLowerCase()).containsAnyOf("friendly", "encouraging", "supportive");
    }//testGenerateInterviewerPrompt_EasyDifficulty_ContainsFriendlyBehavior


    @Test
    void testGenerateInterviewerPrompt_HardDifficulty_ContainsChallengingBehavior() {
        String prompt = promptService.generateInterviewerPrompt("Developer", "Hard", "en");

        assertThat(prompt.toLowerCase()).containsAnyOf("challenging", "probing", "pressure");
    }//testGenerateInterviewerPrompt_HardDifficulty_ContainsChallengingBehavior


    @Test
    void testGenerateInterviewerPrompt_BackendPosition_ContainsTechnicalFocus() {
        String prompt = promptService.generateInterviewerPrompt("Java Backend Developer", "Standard", "en");

        assertThat(prompt.toLowerCase()).containsAnyOf("object-oriented", "api", "database");
    }//testGenerateInterviewerPrompt_BackendPosition_ContainsTechnicalFocus


    @Test
    void testGenerateInterviewerPrompt_QaPosition_ContainsTestingFocus() {
        String prompt = promptService.generateInterviewerPrompt("QA Engineer", "Standard", "en");

        assertThat(prompt.toLowerCase()).containsAnyOf("testing", "test case", "automation");
    }//testGenerateInterviewerPrompt_QaPosition_ContainsTestingFocus


    // ===== Interview Conclusion Detection Tests =====

    @Test
    void testIsInterviewConcluding_DetectsThankYouForYourTime() {
        boolean result = promptService.isInterviewConcluding("Thank you for your time today.");
        assertThat(result).isTrue();
    }//testIsInterviewConcluding_DetectsThankYouForYourTime


    @Test
    void testIsInterviewConcluding_DetectsWeWillBeInTouch() {
        boolean result = promptService.isInterviewConcluding("We'll be in touch with next steps.");
        assertThat(result).isTrue();
    }//testIsInterviewConcluding_DetectsWeWillBeInTouch


    @Test
    void testIsInterviewConcluding_DetectsBestOfLuck() {
        boolean result = promptService.isInterviewConcluding("Best of luck with your career!");
        assertThat(result).isTrue();
    }//testIsInterviewConcluding_DetectsBestOfLuck


    @Test
    void testIsInterviewConcluding_DetectsThatConcludesOurInterview() {
        boolean result = promptService.isInterviewConcluding("That concludes our interview for today.");
        assertThat(result).isTrue();
    }//testIsInterviewConcluding_DetectsThatConcludesOurInterview


    @Test
    void testIsInterviewConcluding_DetectsBulgarianThankYou() {
        // Pattern: "благодаря (ви |)за (отделеното |)време" - case insensitive seems to not work for Cyrillic
        boolean result = promptService.isInterviewConcluding("благодаря ви за отделеното време");
        assertThat(result).isTrue();
    }//testIsInterviewConcluding_DetectsBulgarianThankYou


    @Test
    void testIsInterviewConcluding_DetectsBulgarianGoodbye() {
        boolean result = promptService.isInterviewConcluding("Довиждане и успех!");
        assertThat(result).isTrue();
    }//testIsInterviewConcluding_DetectsBulgarianGoodbye


    @Test
    void testIsInterviewConcluding_DetectsBulgarianSuccess() {
        // Pattern: "успех" - simple pattern that should match
        boolean result = promptService.isInterviewConcluding("Желая ви успех!");
        assertThat(result).isTrue();
    }//testIsInterviewConcluding_DetectsBulgarianSuccess


    @Test
    void testIsInterviewConcluding_ReturnsFalseForNormalQuestion() {
        boolean result = promptService.isInterviewConcluding("Tell me about your experience with Java.");
        assertThat(result).isFalse();
    }//testIsInterviewConcluding_ReturnsFalseForNormalQuestion


    @Test
    void testIsInterviewConcluding_ReturnsFalseForNullInput() {
        boolean result = promptService.isInterviewConcluding(null);
        assertThat(result).isFalse();
    }//testIsInterviewConcluding_ReturnsFalseForNullInput


    @Test
    void testIsInterviewConcluding_ReturnsFalseForBlankInput() {
        boolean result = promptService.isInterviewConcluding("   ");
        assertThat(result).isFalse();
    }//testIsInterviewConcluding_ReturnsFalseForBlankInput


    @Test
    void testIsInterviewConcluding_ReturnsFalseForTechnicalDiscussion() {
        boolean result = promptService.isInterviewConcluding(
                "Can you explain how you would design a REST API for a booking system?");
        assertThat(result).isFalse();
    }//testIsInterviewConcluding_ReturnsFalseForTechnicalDiscussion

}//InterviewPromptServiceTest
