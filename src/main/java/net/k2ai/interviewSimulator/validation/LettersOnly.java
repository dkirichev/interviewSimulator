package net.k2ai.interviewSimulator.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string contains only letters (Latin and Cyrillic), spaces, and hyphens.
 * Used for candidate names where numbers and special characters are not allowed.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LettersOnlyValidator.class)
public @interface LettersOnly {

	String message() default "{validation.lettersOnly}";

	int min() default 2;

	int max() default 30;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
