package net.k2ai.interviewSimulator.service;

import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(ReplaceCamelCase.class)
class InputSanitizerServiceTest {

    private InputSanitizerService sanitizerService;


    @BeforeEach
    void setUp() {
        sanitizerService = new InputSanitizerService();
    }//setUp


    // ===== sanitizeForHtml Tests =====

    @Test
    void testSanitizeForHtml_EscapesAmpersand() {
        String result = sanitizerService.sanitizeForHtml("Tom & Jerry");
        assertThat(result).isEqualTo("Tom &amp; Jerry");
    }//testSanitizeForHtml_EscapesAmpersand


    @Test
    void testSanitizeForHtml_EscapesLessThan() {
        String result = sanitizerService.sanitizeForHtml("<script>");
        assertThat(result).isEqualTo("&lt;script&gt;");
    }//testSanitizeForHtml_EscapesLessThan


    @Test
    void testSanitizeForHtml_EscapesQuotes() {
        String result = sanitizerService.sanitizeForHtml("He said \"hello\"");
        assertThat(result).isEqualTo("He said &quot;hello&quot;");
    }//testSanitizeForHtml_EscapesQuotes


    @Test
    void testSanitizeForHtml_ReturnsNullForNull() {
        String result = sanitizerService.sanitizeForHtml(null);
        assertThat(result).isNull();
    }//testSanitizeForHtml_ReturnsNullForNull


    // ===== sanitizeForPrompt Tests =====

    @Test
    void testSanitizeForPrompt_RemovesScriptTags() {
        String result = sanitizerService.sanitizeForPrompt("Hello <script>alert('xss')</script> World");
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("</script>");
    }//testSanitizeForPrompt_RemovesScriptTags


    @Test
    void testSanitizeForPrompt_RemovesHtmlTags() {
        String result = sanitizerService.sanitizeForPrompt("<div>Hello</div>");
        assertThat(result).isEqualTo("Hello");
    }//testSanitizeForPrompt_RemovesHtmlTags


    @Test
    void testSanitizeForPrompt_NormalizesWhitespace() {
        String result = sanitizerService.sanitizeForPrompt("Hello    World");
        assertThat(result).isEqualTo("Hello World");
    }//testSanitizeForPrompt_NormalizesWhitespace


    @Test
    void testSanitizeForPrompt_ReturnsNullForNull() {
        String result = sanitizerService.sanitizeForPrompt(null);
        assertThat(result).isNull();
    }//testSanitizeForPrompt_ReturnsNullForNull


    // ===== sanitizeName Tests =====

    @Test
    void testSanitizeName_AcceptsLatinLetters() {
        String result = sanitizerService.sanitizeName("John Doe");
        assertThat(result).isEqualTo("John Doe");
    }//testSanitizeName_AcceptsLatinLetters


    @Test
    void testSanitizeName_AcceptsCyrillicLetters() {
        String result = sanitizerService.sanitizeName("Иван Петров");
        assertThat(result).isEqualTo("Иван Петров");
    }//testSanitizeName_AcceptsCyrillicLetters


    @Test
    void testSanitizeName_AcceptsHyphenatedNames() {
        String result = sanitizerService.sanitizeName("Mary-Jane");
        assertThat(result).isEqualTo("Mary-Jane");
    }//testSanitizeName_AcceptsHyphenatedNames


    @Test
    void testSanitizeName_AcceptsApostrophe() {
        String result = sanitizerService.sanitizeName("O'Connor");
        assertThat(result).isEqualTo("O'Connor");
    }//testSanitizeName_AcceptsApostrophe


    @Test
    void testSanitizeName_RejectsNumbers() {
        String result = sanitizerService.sanitizeName("John123");
        assertThat(result).isNull();
    }//testSanitizeName_RejectsNumbers


    @Test
    void testSanitizeName_RejectsSpecialCharacters() {
        String result = sanitizerService.sanitizeName("John@Doe");
        assertThat(result).isNull();
    }//testSanitizeName_RejectsSpecialCharacters


    @Test
    void testSanitizeName_ReturnsNullForBlank() {
        String result = sanitizerService.sanitizeName("   ");
        assertThat(result).isNull();
    }//testSanitizeName_ReturnsNullForBlank


    @Test
    void testSanitizeName_TruncatesLongNames() {
        String longName = "A".repeat(50);
        String result = sanitizerService.sanitizeName(longName);
        assertThat(result).hasSize(30);
    }//testSanitizeName_TruncatesLongNames


    // ===== sanitizePosition Tests =====

    @Test
    void testSanitizePosition_AcceptsValidPosition() {
        String result = sanitizerService.sanitizePosition("Java Developer");
        assertThat(result).isEqualTo("Java Developer");
    }//testSanitizePosition_AcceptsValidPosition


    @Test
    void testSanitizePosition_AcceptsPositionWithNumbers() {
        String result = sanitizerService.sanitizePosition("Senior Java Developer (5+ years)");
        assertThat(result).isEqualTo("Senior Java Developer (5+ years)");
    }//testSanitizePosition_AcceptsPositionWithNumbers


    @Test
    void testSanitizePosition_RejectsSqlInjection() {
        String result = sanitizerService.sanitizePosition("'; DROP TABLE users; --");
        assertThat(result).isNull();
    }//testSanitizePosition_RejectsSqlInjection


    @Test
    void testSanitizePosition_RejectsScriptTags() {
        String result = sanitizerService.sanitizePosition("<script>alert('xss')</script>");
        assertThat(result).isNull();
    }//testSanitizePosition_RejectsScriptTags


    @Test
    void testSanitizePosition_TruncatesLongPosition() {
        String longPosition = "A".repeat(100);
        String result = sanitizerService.sanitizePosition(longPosition);
        assertThat(result).hasSize(50);
    }//testSanitizePosition_TruncatesLongPosition


    // ===== sanitizeCvText Tests =====

    @Test
    void testSanitizeCvText_RemovesScriptTags() {
        String result = sanitizerService.sanitizeCvText("Experience <script>bad</script> in Java");
        assertThat(result).doesNotContain("<script>");
    }//testSanitizeCvText_RemovesScriptTags


    @Test
    void testSanitizeCvText_RemovesHtmlTags() {
        String result = sanitizerService.sanitizeCvText("<b>Bold</b> text");
        assertThat(result).isEqualTo("Bold text");
    }//testSanitizeCvText_RemovesHtmlTags


    @Test
    void testSanitizeCvText_TruncatesVeryLongText() {
        String longText = "A".repeat(150_000);
        String result = sanitizerService.sanitizeCvText(longText);
        assertThat(result).hasSize(100_000);
    }//testSanitizeCvText_TruncatesVeryLongText


    @Test
    void testSanitizeCvText_ReturnsNullForNull() {
        String result = sanitizerService.sanitizeCvText(null);
        assertThat(result).isNull();
    }//testSanitizeCvText_ReturnsNullForNull


    // ===== validateEnum Tests =====

    @Test
    void testValidateEnum_ReturnsValidValue() {
        String[] allowed = {"Easy", "Standard", "Hard"};
        String result = sanitizerService.validateEnum("Standard", allowed, "Easy");
        assertThat(result).isEqualTo("Standard");
    }//testValidateEnum_ReturnsValidValue


    @Test
    void testValidateEnum_ReturnsDefaultForInvalid() {
        String[] allowed = {"Easy", "Standard", "Hard"};
        String result = sanitizerService.validateEnum("Invalid", allowed, "Easy");
        assertThat(result).isEqualTo("Easy");
    }//testValidateEnum_ReturnsDefaultForInvalid


    @Test
    void testValidateEnum_ReturnsDefaultForNull() {
        String[] allowed = {"Easy", "Standard", "Hard"};
        String result = sanitizerService.validateEnum(null, allowed, "Easy");
        assertThat(result).isEqualTo("Easy");
    }//testValidateEnum_ReturnsDefaultForNull


    @Test
    void testValidateEnum_ReturnsDefaultForBlank() {
        String[] allowed = {"Easy", "Standard", "Hard"};
        String result = sanitizerService.validateEnum("   ", allowed, "Easy");
        assertThat(result).isEqualTo("Easy");
    }//testValidateEnum_ReturnsDefaultForBlank

}//InputSanitizerServiceTest
