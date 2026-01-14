package com.m00nreport.reporter;

import com.m00nreport.reporter.model.TestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JUnit 5 Extension for M00n Report integration.
 * 
 * <p>This extension is automatically registered via ServiceLoader.
 * No annotation needed on test classes.</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Automatic test lifecycle tracking</li>
 *   <li>Retry detection (for @RetryingTest)</li>
 *   <li>Thread-safe parallel execution</li>
 *   <li>Catches intermediate retry failures via InvocationInterceptor</li>
 * </ul>
 */
public class M00nExtension implements 
        BeforeEachCallback,
        AfterEachCallback,
        TestWatcher,
        InvocationInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(M00nExtension.class);
    
    private static final ExtensionContext.Namespace NAMESPACE = 
        ExtensionContext.Namespace.create(M00nExtension.class);
    private static final String TEST_RESULT_KEY = "testResult";
    
    // Thread-local to track if test was already reported (avoids store access after close)
    // This is needed because JUnit closes the store between retry attempts in @RetryingTest
    private static final ThreadLocal<Boolean> reportedInCurrentTest = ThreadLocal.withInitial(() -> false);
    
    // Pattern to detect retry attempt from display name
    // Matches: "Attempt 1", "- Attempt 2", "Test name - Attempt 3", "[2]", "#1", etc.
    private static final Pattern RETRY_PATTERN = Pattern.compile(
        "(?:[-–#]\\s*)?(?:Attempt\\s*|\\[#?)(\\d+)\\]?", 
        Pattern.CASE_INSENSITIVE
    );
    
    // =========================================================================
    // Test Lifecycle
    // =========================================================================
    
    @Override
    public void beforeEach(ExtensionContext context) {
        try {
            M00nReporter reporter = M00nReporter.getInstance();
            if (!reporter.isEnabled()) return;
            
            // Extract display names for titlePath
            String suiteName = getSuiteDisplayName(context);
            
            // Get test name: prefer @DisplayName on method, fallback to method name
            String testName = getTestDisplayName(context);
            
            // Get retry attempt from context (handles @RetryingTest)
            // attempt is 1-indexed, retry is 0-indexed
            int attempt = getRetryAttemptFromContext(context);
            int retry = attempt > 0 ? attempt - 1 : 0;
            
            // Debug: log what we're detecting
            if (log.isDebugEnabled()) {
                log.debug("[M00nExtension] beforeEach - displayName: '{}', uniqueId: '{}', attempt: {}, retry: {}", 
                    context.getDisplayName(), 
                    context.getUniqueId().substring(Math.max(0, context.getUniqueId().length() - 50)), 
                    attempt, retry);
            }
            
            // Keep actual class name for filePath
            String className = context.getRequiredTestClass().getName();
            
            // Pass retry count to startTest so it's included in the API request
            TestResult result = reporter.startTest(suiteName, testName, className, retry);
            if (result != null) {
                log.debug("[M00nExtension] Test started: suite='{}', test='{}', retry={}", 
                    suiteName, testName, retry);
                
                // Store in extension context
                context.getStore(NAMESPACE).put(TEST_RESULT_KEY, result);
                // Clear the reported flag for this new test/retry attempt
                // Use thread-local to avoid "store closed" errors with @RetryingTest
                reportedInCurrentTest.set(false);
                // Set thread-local for StepProxy
                M00nStep.setCurrentTest(result);
            }
        } catch (Exception e) {
            log.warn("[M00nExtension] beforeEach failed: {}", e.getMessage());
        }
    }
    
    @Override
    public void afterEach(ExtensionContext context) {
        // Always clean up thread-locals
        M00nStep.clearCurrentTest();
        reportedInCurrentTest.remove();
    }
    
    /**
     * Intercept test method execution to catch results BEFORE JUnit Pioneer's retry logic.
     * This ensures each retry attempt is properly reported, not just the final result.
     */
    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        interceptTest(invocation, extensionContext);
    }
    
    /**
     * Intercept test template method execution (for @RetryingTest, @ParameterizedTest, etc.).
     * This ensures each retry attempt is properly reported.
     */
    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        interceptTest(invocation, extensionContext);
    }
    
    /**
     * Common test interception logic for both regular tests and test templates.
     */
    private void interceptTest(Invocation<Void> invocation, ExtensionContext extensionContext) throws Throwable {
        TestResult testResult = getTestResultSafely(extensionContext);
        
        if (testResult == null) {
            // Reporter not tracking this test, just proceed
            invocation.proceed();
            return;
        }
        
        try {
            invocation.proceed();
            // Test passed - report immediately
            reportTestResult(extensionContext, "passed", null);
        } catch (Throwable t) {
            // Mark test as failed for screenshot capture in @AfterEach
            M00nStep.markFailed();
            // Test failed - report immediately (before JUnit Pioneer catches for retry)
            reportTestResult(extensionContext, "failed", t);
            throw t; // Re-throw so JUnit Pioneer can handle retry
        }
    }
    
    /**
     * Report test result immediately. Used by interceptTestMethod to ensure
     * each retry attempt is reported, not just the final result.
     */
    private void reportTestResult(ExtensionContext context, String status, Throwable error) {
        try {
            // Use thread-local to check if already reported (avoids store access after close)
            if (reportedInCurrentTest.get()) {
                return; // Already reported by interceptor
            }
            
            TestResult testResult = getTestResultSafely(context);
            if (testResult == null) return;
            
            // Mark as reported using thread-local (store may be closed for @RetryingTest)
            reportedInCurrentTest.set(true);
            
            M00nReporter reporter = M00nReporter.getInstance();
            if (!reporter.isEnabled()) return;
            
            String suiteName = getSuiteDisplayName(context);
            String testName = getTestDisplayName(context);
            
            log.debug("[M00nExtension] Reporting test via interceptor: suite='{}', test='{}', status='{}', retry={}",
                suiteName, testName, status, testResult.getRetry());
            
            reporter.completeTest(suiteName, testName, status, error);
        } catch (Exception e) {
            log.warn("[M00nExtension] reportTestResult failed: {}", e.getMessage());
        }
    }
    
    // =========================================================================
    // TestWatcher - Final test status
    // =========================================================================
    
    @Override
    public void testSuccessful(ExtensionContext context) {
        completeTest(context, "passed", null);
    }
    
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        completeTest(context, "failed", cause);
    }
    
    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        // JUnit Pioneer's @RetryingTest calls testAborted when a retry attempt fails
        // but wraps the failure in TestAbortedException. We need to detect this and
        // report as "failed" not "skipped".
        //
        // Real skips (Assumptions.assumeTrue) have TestAbortedException without a cause.
        // Retry failures have TestAbortedException WITH a wrapped cause or message.
        String status = "skipped";
        
        if (cause != null) {
            if (cause instanceof org.opentest4j.TestAbortedException) {
                // Check if it's a wrapped failure (has cause) or has a failure-like message
                if (cause.getCause() != null || 
                    (cause.getMessage() != null && !cause.getMessage().contains("Assumption"))) {
                    status = "failed";
                }
            } else {
                // Any other exception is a real failure
                status = "failed";
            }
        }
        
        completeTest(context, status, cause);
    }
    
    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        try {
            M00nReporter reporter = M00nReporter.getInstance();
            if (!reporter.isEnabled()) return;
            
            String suiteName = getSuiteDisplayName(context);
            String testName = context.getDisplayName();
            String className = context.getRequiredTestClass().getName();
            
            // Disabled tests are always first run (retry=0)
            TestResult result = reporter.startTest(suiteName, testName, className, 0);
            if (result != null) {
                reporter.completeTest(suiteName, testName, "skipped", null);
            }
        } catch (Exception e) {
            log.warn("[M00nExtension] testDisabled failed: {}", e.getMessage());
        }
    }
    
    private void completeTest(ExtensionContext context, String status, Throwable error) {
        try {
            // Check if already reported by interceptor using thread-local
            // (avoids store access after close for @RetryingTest scenarios)
            if (reportedInCurrentTest.get()) {
                log.debug("[M00nExtension] Test already reported by interceptor, skipping TestWatcher callback");
                return;
            }
            
            M00nReporter reporter = M00nReporter.getInstance();
            if (!reporter.isEnabled()) return;
            
            String suiteName = getSuiteDisplayName(context);
            String testName = getTestDisplayName(context);
            
            // Mark as reported before calling completeTest
            reportedInCurrentTest.set(true);
            
            reporter.completeTest(suiteName, testName, status, error);
        } catch (Exception e) {
            log.warn("[M00nExtension] completeTest failed: {}", e.getMessage());
        }
    }
    
    /**
     * Safely get TestResult from context store, handling closed store gracefully.
     * JUnit 5 closes the store between retry attempts in @RetryingTest, which can
     * cause "NamespacedHierarchicalStore cannot be modified after closed" errors.
     */
    private TestResult getTestResultSafely(ExtensionContext context) {
        try {
            return context.getStore(NAMESPACE).get(TEST_RESULT_KEY, TestResult.class);
        } catch (Exception e) {
            // Store is closed (e.g., between retry attempts)
            log.debug("[M00nExtension] Store already closed, using thread-local fallback");
            return M00nStep.current().orElse(null);
        }
    }
    
    // =========================================================================
    // Helpers
    // =========================================================================
    
    /**
     * Gets the suite display name from @DisplayName on class or simple class name.
     */
    private String getSuiteDisplayName(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        
        DisplayName annotation = testClass.getAnnotation(DisplayName.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        
        return testClass.getSimpleName();
    }
    
    /**
     * Gets the test display name from @DisplayName on method or method name.
     * For @RetryingTest, extracts the base name by stripping the attempt suffix.
     */
    private String getTestDisplayName(ExtensionContext context) {
        try {
            var method = context.getRequiredTestMethod();
            
            // Check for @DisplayName on method
            DisplayName displayName = method.getAnnotation(DisplayName.class);
            if (displayName != null && !displayName.value().isEmpty()) {
                return displayName.value();
            }
            
            // For @RetryingTest, context.getDisplayName() contains the template
            // e.g., "🔄 Flaky test - Attempt 1"
            // We need to strip the attempt suffix to get the base test name
            String contextDisplayName = context.getDisplayName();
            if (contextDisplayName != null && RETRY_PATTERN.matcher(contextDisplayName).find()) {
                // Strip the attempt part: "🔄 Flaky test - Attempt 1" -> "🔄 Flaky test"
                String baseName = RETRY_PATTERN.matcher(contextDisplayName)
                    .replaceAll("")
                    .replaceAll("\\s*[-–]\\s*$", "")  // Remove trailing dashes
                    .trim();
                if (!baseName.isEmpty()) {
                    return baseName;
                }
            }
            
            // Fallback to method name (humanized)
            return humanizeMethodName(method.getName());
        } catch (Exception e) {
            // Fallback to context display name
            return context.getDisplayName();
        }
    }
    
    /**
     * Converts camelCase method name to readable format.
     * e.g., "testUserLogin" -> "Test user login"
     */
    private String humanizeMethodName(String methodName) {
        if (methodName == null || methodName.isEmpty()) {
            return methodName;
        }
        
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
    
    /**
     * Parses retry information from test display name.
     * Handles patterns like "Attempt 1", "Attempt 2", "Test name [2]", etc.
     */
    private RetryInfo parseRetryInfo(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return new RetryInfo(displayName, 0);
        }
        
        Matcher matcher = RETRY_PATTERN.matcher(displayName);
        
        if (matcher.find()) {
            int attempt = Integer.parseInt(matcher.group(1));
            // Remove the attempt suffix from the name
            String cleanName = displayName.substring(0, matcher.start()).trim();
            // Remove trailing delimiters
            cleanName = cleanName.replaceAll("\\s*[:\\-–]\\s*$", "").trim();
            
            log.debug("[M00nExtension] parseRetryInfo: input='{}', matched='{}', attempt={}", 
                displayName, matcher.group(0), attempt);
            
            return new RetryInfo(cleanName.isEmpty() ? displayName : cleanName, attempt);
        }
        
        log.debug("[M00nExtension] parseRetryInfo: input='{}', no match found", displayName);
        return new RetryInfo(displayName, 0);
    }
    
    /**
     * Gets retry attempt from extension context.
     * JUnit Pioneer stores retry info in the unique ID and display name.
     */
    private int getRetryAttemptFromContext(ExtensionContext context) {
        String displayName = context.getDisplayName();
        String uniqueId = context.getUniqueId();
        
        log.debug("[M00nExtension] getRetryAttemptFromContext - displayName='{}', uniqueId='{}'", 
            displayName, uniqueId);
        
        // Method 1: Parse from display name (works for @RetryingTest with name="... Attempt {index}")
        RetryInfo info = parseRetryInfo(displayName);
        if (info.attempt > 0) {
            log.debug("[M00nExtension] Found attempt {} from displayName", info.attempt);
            return info.attempt;
        }
        
        // Method 2: Check unique ID for retry info (JUnit Pioneer pattern)
        // Pattern: [test-template-invocation:#1] or similar
        Matcher idMatcher = Pattern.compile("\\[test-template-invocation:#(\\d+)\\]").matcher(uniqueId);
        if (idMatcher.find()) {
            int attempt = Integer.parseInt(idMatcher.group(1));
            log.debug("[M00nExtension] Found attempt {} from uniqueId", attempt);
            return attempt;
        }
        
        // Method 3: Check for repetition index pattern in uniqueId
        // Pattern: [repetition-index:1] or similar  
        Matcher repMatcher = Pattern.compile("\\[(?:repetition|invocation)[-_]?(?:index)?[:#](\\d+)\\]", 
            Pattern.CASE_INSENSITIVE).matcher(uniqueId);
        if (repMatcher.find()) {
            int attempt = Integer.parseInt(repMatcher.group(1));
            log.debug("[M00nExtension] Found attempt {} from repetition pattern in uniqueId", attempt);
            return attempt;
        }
        
        // Method 4: Just look for any number pattern after "Attempt" in display name
        Matcher simpleAttempt = Pattern.compile("Attempt\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(displayName);
        if (simpleAttempt.find()) {
            int attempt = Integer.parseInt(simpleAttempt.group(1));
            log.debug("[M00nExtension] Found attempt {} from simple Attempt pattern", attempt);
            return attempt;
        }
        
        log.debug("[M00nExtension] No retry attempt detected, returning 0");
        return 0;
    }
    
    private static class RetryInfo {
        final String cleanName;
        final int attempt;
        
        RetryInfo(String cleanName, int attempt) {
            this.cleanName = cleanName;
            this.attempt = attempt;
        }
    }
}
