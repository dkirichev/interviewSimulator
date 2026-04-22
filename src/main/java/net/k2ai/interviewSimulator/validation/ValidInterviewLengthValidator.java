package net.k2ai.interviewSimulator.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class ValidInterviewLengthValidator implements ConstraintValidator<ValidInterviewLength, String> {

	private static final Set<String> VALID_LENGTHS = Set.of("Quick", "Standard", "Marathon");

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			return true; // Let @NotBlank handle empty values
		}
		return VALID_LENGTHS.contains(value);
	}

}
