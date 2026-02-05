package net.k2ai.interviewSimulator.testutil;

import org.junit.jupiter.api.DisplayNameGenerator;

import java.lang.reflect.Method;

public class ReplaceCamelCase extends DisplayNameGenerator.Standard {


    @Override
    public String generateDisplayNameForClass(Class<?> testClass) {
        return replaceCamelCase(super.generateDisplayNameForClass(testClass));
    }//generateDisplayNameForClass


    @Override
    public String generateDisplayNameForNestedClass(Class<?> nestedClass) {
        return replaceCamelCase(super.generateDisplayNameForNestedClass(nestedClass));
    }//generateDisplayNameForNestedClass


    @Override
    public String generateDisplayNameForMethod(Class<?> testClass, Method testMethod) {
        return replaceCamelCase(testMethod.getName());
    }//generateDisplayNameForMethod


    private String replaceCamelCase(String camelCase) {
        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(camelCase.charAt(0)));

        for (int i = 1; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append(' ');
                result.append(Character.toLowerCase(c));
            } else if (c == '_') {
                result.append(' ');
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }//replaceCamelCase

}//ReplaceCamelCase
