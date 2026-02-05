package net.k2ai.interviewSimulator.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * Validator for @ValidVoice annotation.
 * Ensures the voice ID is one of: Algieba, Kore, Fenrir, Despina.
 */
public class ValidVoiceValidator implements ConstraintValidator<ValidVoice, String> {

	private static final Set<String> VALID_VOICES = Set.of("Algieba", "Kore", "Fenrir", "Despina");

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			return true; // Let @NotBlank handle empty values
		}
		return VALID_VOICES.contains(value);
	}

}
