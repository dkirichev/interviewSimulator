package net.k2ai.interviewSimulator.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * Validator for @ValidLanguage annotation.
 * Ensures the language value is one of: en, bg.
 */
public class ValidLanguageValidator implements ConstraintValidator<ValidLanguage, String> {

    private static final Set<String> VALID_LANGUAGES = Set.of("en", "bg");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // Let @NotBlank handle empty values
        }
        return VALID_LANGUAGES.contains(value);
    }

}
