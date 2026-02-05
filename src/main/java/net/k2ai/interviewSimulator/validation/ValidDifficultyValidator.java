package net.k2ai.interviewSimulator.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * Validator for @ValidDifficulty annotation.
 * Ensures the difficulty value is one of: Easy, Standard, Hard.
 */
public class ValidDifficultyValidator implements ConstraintValidator<ValidDifficulty, String> {

	private static final Set<String> VALID_DIFFICULTIES = Set.of("Easy", "Standard", "Hard");

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			return true; // Let @NotBlank handle empty values
		}
		return VALID_DIFFICULTIES.contains(value);
	}

}
