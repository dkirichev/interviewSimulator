package net.k2ai.interviewSimulator.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidInterviewLengthValidator.class)
public @interface ValidInterviewLength {

	String message() default "{validation.interviewLength.invalid}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
