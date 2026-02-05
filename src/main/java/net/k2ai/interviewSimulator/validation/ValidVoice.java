package net.k2ai.interviewSimulator.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that the value is one of the allowed voice IDs: Algieba, Kore, Fenrir, Despina.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidVoiceValidator.class)
public @interface ValidVoice {

	String message() default "{validation.voice.invalid}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
