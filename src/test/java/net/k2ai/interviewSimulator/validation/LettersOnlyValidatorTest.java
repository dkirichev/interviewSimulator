package net.k2ai.interviewSimulator.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.k2ai.interviewSimulator.testutil.ReplaceCamelCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(ReplaceCamelCase.class)
class LettersOnlyValidatorTest {

    private LettersOnlyValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new LettersOnlyValidator();

        // Initialize with default values (min=2, max=30)
        LettersOnly annotation = mock(LettersOnly.class);
        when(annotation.min()).thenReturn(2);
        when(annotation.max()).thenReturn(30);
        validator.initialize(annotation);

        // Mock the context for validation messages
        when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(violationBuilder);
    }//setUp


    // ===== Valid Input Tests =====

    @Test
    void testValid_LatinLetters() {
        boolean result = validator.isValid("John Doe", context);
        assertThat(result).isTrue();
    }//testValid_LatinLetters


    @Test
    void testValid_CyrillicLetters() {
        boolean result = validator.isValid("Иван Петров", context);
        assertThat(result).isTrue();
    }//testValid_CyrillicLetters


    @Test
    void testValid_MixedLatinAndCyrillic() {
        boolean result = validator.isValid("Ivan Иванов", context);
        assertThat(result).isTrue();
    }//testValid_MixedLatinAndCyrillic


    @Test
    void testValid_NameWithHyphen() {
        boolean result = validator.isValid("Mary-Jane", context);
        assertThat(result).isTrue();
    }//testValid_NameWithHyphen


    @Test
    void testValid_NameWithApostrophe() {
        boolean result = validator.isValid("O'Connor", context);
        assertThat(result).isTrue();
    }//testValid_NameWithApostrophe


    @Test
    void testValid_NullInput_DelegatesTo_NotBlank() {
        // Null should return true - let @NotBlank handle null values
        boolean result = validator.isValid(null, context);
        assertThat(result).isTrue();
    }//testValid_NullInput_DelegatesTo_NotBlank


    @Test
    void testValid_BlankInput_DelegatesTo_NotBlank() {
        // Blank should return true - let @NotBlank handle blank values
        boolean result = validator.isValid("   ", context);
        assertThat(result).isTrue();
    }//testValid_BlankInput_DelegatesTo_NotBlank


    // ===== Invalid Input Tests =====

    @Test
    void testInvalid_ContainsNumbers() {
        boolean result = validator.isValid("John123", context);
        assertThat(result).isFalse();
    }//testInvalid_ContainsNumbers


    @Test
    void testInvalid_ContainsAtSign() {
        boolean result = validator.isValid("john@doe", context);
        assertThat(result).isFalse();
    }//testInvalid_ContainsAtSign


    @Test
    void testInvalid_ContainsExclamation() {
        boolean result = validator.isValid("John!", context);
        assertThat(result).isFalse();
    }//testInvalid_ContainsExclamation


    @Test
    void testInvalid_ContainsParentheses() {
        boolean result = validator.isValid("John (Jr)", context);
        assertThat(result).isFalse();
    }//testInvalid_ContainsParentheses


    @Test
    void testInvalid_TooShort() {
        boolean result = validator.isValid("A", context);
        assertThat(result).isFalse();
    }//testInvalid_TooShort


    @Test
    void testInvalid_TooLong() {
        String longName = "A".repeat(31);
        boolean result = validator.isValid(longName, context);
        assertThat(result).isFalse();
    }//testInvalid_TooLong


    @Test
    void testInvalid_ContainsUnderscore() {
        boolean result = validator.isValid("John_Doe", context);
        assertThat(result).isFalse();
    }//testInvalid_ContainsUnderscore


    @Test
    void testInvalid_ContainsDot() {
        boolean result = validator.isValid("Dr. John", context);
        assertThat(result).isFalse();
    }//testInvalid_ContainsDot

}//LettersOnlyValidatorTest
