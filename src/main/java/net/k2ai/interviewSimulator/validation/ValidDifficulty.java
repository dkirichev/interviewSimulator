package net.k2ai.interviewSimulator.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that the value is one of the allowed difficulty levels: Easy, Standard, Hard.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDifficultyValidator.class)
public @interface ValidDifficulty {

    String message() default "{validation.difficulty.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
