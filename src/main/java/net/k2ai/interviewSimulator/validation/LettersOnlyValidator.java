package net.k2ai.interviewSimulator.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator for @LettersOnly annotation.
 * Allows Latin letters, Cyrillic letters, spaces, hyphens, and apostrophes.
 * Does NOT allow numbers or special characters.
 */
public class LettersOnlyValidator implements ConstraintValidator<LettersOnly, String> {

	// Unicode ranges: Latin letters, Cyrillic letters, spaces, hyphens, apostrophes
	private static final Pattern LETTERS_ONLY_PATTERN = Pattern.compile(
			"^[\\p{L}\\s\\-']+$", Pattern.UNICODE_CHARACTER_CLASS
	);

	private int min;
	private int max;

	@Override
	public void initialize(LettersOnly annotation) {
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
			context.buildConstraintViolationWithTemplate("{validation.name.size}")
					.addConstraintViolation();
			return false;
		}

		// Check pattern - only letters, spaces, hyphens, apostrophes
		if (!LETTERS_ONLY_PATTERN.matcher(trimmed).matches()) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate("{validation.lettersOnly}")
					.addConstraintViolation();
			return false;
		}

		return true;
	}

}
