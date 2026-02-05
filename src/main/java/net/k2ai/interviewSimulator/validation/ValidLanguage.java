package net.k2ai.interviewSimulator.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that the value is one of the allowed languages: en, bg.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidLanguageValidator.class)
public @interface ValidLanguage {

    String message() default "{validation.language.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
