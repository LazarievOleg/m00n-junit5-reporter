package com.m00nreport.reporter.annotations;

import java.lang.annotation.*;

/**
 * Marks a method as a test step for M00n Report.
 * 
 * When a method annotated with @Step is called through a StepProxy,
 * it will be automatically tracked as a step in the test report.
 * 
 * Usage:
 * <pre>
 * interface LoginPage {
 *     @Step("Navigate to login page")
 *     void open();
 *     
 *     @Step("Enter credentials")
 *     void enterCredentials(String user, String pass);
 *     
 *     @Step  // Uses method name as title
 *     void clickLoginButton();
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Step {
    
    /**
     * Step description/title.
     * If empty, the method name will be converted to a readable format.
     * Example: "clickLoginButton" → "Click login button"
     */
    String value() default "";
}
