package net.k2ai.interviewSimulator.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string contains only safe text characters.
 * Allows letters (Latin/Cyrillic), numbers, spaces, and common punctuation.
 * Used for custom position input where more flexibility is needed.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeTextValidator.class)
public @interface SafeText {

    String message() default "{validation.safeText}";

    int min() default 2;

    int max() default 50;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
