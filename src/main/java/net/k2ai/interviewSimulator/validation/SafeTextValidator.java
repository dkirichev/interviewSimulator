package net.k2ai.interviewSimulator.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator for @SafeText annotation.
 * Allows letters (Latin/Cyrillic), numbers, spaces, and common punctuation: . , + - # ( ) / &
 * Blocks dangerous characters: < > " ' ; ` \ { } | etc.
 */
public class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    // Safe characters: letters, numbers, spaces, and limited punctuation
    private static final Pattern SAFE_TEXT_PATTERN = Pattern.compile(
            "^[\\p{L}\\p{N}\\s.,+\\-#()/&]+$", Pattern.UNICODE_CHARACTER_CLASS
    );

    // Dangerous patterns to explicitly block
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "[<>\"';`\\\\{}|\\[\\]^~]|javascript:|data:|on\\w+=", Pattern.CASE_INSENSITIVE
    );

    private int min;
    private int max;

    @Override
    public void initialize(SafeText annotation) {
        this.min = annotation.min();
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // Let @NotBlank handle empty values
        }

        String trimmed = value.trim();

        // Check length
        if (trimmed.length() < min || trimmed.length() > max) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("{validation.customPosition.size}")
                    .addConstraintViolation();
            return false;
        }

        // Block dangerous patterns first
        if (DANGEROUS_PATTERN.matcher(trimmed).find()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("{validation.safeText.dangerous}")
                    .addConstraintViolation();
            return false;
        }

        // Check that only safe characters are used
        if (!SAFE_TEXT_PATTERN.matcher(trimmed).matches()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("{validation.safeText}")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }

}
