package com.m00nreport.reporter;

import com.m00nreport.reporter.annotations.Step;
import com.m00nreport.reporter.model.ErrorData;
import com.m00nreport.reporter.model.TestResult;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * AspectJ aspect for automatically intercepting @Step annotated methods.
 * 
 * <p>This aspect enables automatic step tracking without requiring StepProxy
 * or PageFactory. Simply annotate methods with @Step and they will be
 * automatically tracked when called.</p>
 * 
 * <h2>Setup:</h2>
 * <p>Enable AspectJ load-time weaving by adding to your test JVM args:</p>
 * <pre>{@code
 * -javaagent:path/to/aspectjweaver.jar
 * }</pre>
 * 
 * <p>Or use compile-time weaving with AspectJ compiler.</p>
 * 
 * <h2>Usage:</h2>
 * <pre>{@code
 * public class LoginPage {
 *     @Step("Navigate to login page")
 *     public void open() {
 *         // This will be automatically tracked as a step!
 *     }
 * }
 * }</pre>
 */
@Aspect
public class M00nStepAspect {
    
    /**
     * Intercepts only method EXECUTION (not call) annotated with @Step.
     * Using execution() to avoid duplicate interception at both call-site and execution.
     */
    @Around("execution(@com.m00nreport.reporter.annotations.Step * *(..))")
    public Object interceptStep(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // Extract step title from annotation
        Step stepAnnotation = method.getAnnotation(Step.class);
        String stepTitle = extractStepTitle(stepAnnotation, method);
        
        // Get current test result
        TestResult testResult = M00nStep.getCurrentTest();
        if (testResult == null) {
            // No active test, just proceed without tracking
            return joinPoint.proceed();
        }
        
        // Create and track step
        var step = testResult.addStep(stepTitle, "step");
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            step.setDuration(System.currentTimeMillis() - startTime);
            // Complete step as passed
            testResult.completeStep(step, true, null);
            return result;
        } catch (Throwable t) {
            step.setDuration(System.currentTimeMillis() - startTime);
            // Complete step as failed
            testResult.completeStep(step, false, t);
            throw t;
        }
    }
    
    /**
     * Extracts step title from annotation or generates from method name.
     */
    private String extractStepTitle(Step annotation, Method method) {
        String title = annotation.value();
        if (title == null || title.isEmpty()) {
            title = humanize(method.getName());
        }
        return title;
    }
    
    /**
     * Converts camelCase method name to human-readable format.
     * Example: "clickLoginButton" → "Click login button"
     */
    private String humanize(String methodName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < methodName.length(); i++) {
            char c = methodName.charAt(i);
            if (i == 0) {
                result.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                result.append(' ').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
