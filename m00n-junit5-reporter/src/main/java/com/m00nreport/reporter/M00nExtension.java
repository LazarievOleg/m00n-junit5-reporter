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
    // ONLY matches JUnit Pioneer's @RetryingTest patterns:
    // - "Attempt 1", "Attempt 2" (explicit attempt indicator)
    // - "Test name - Attempt 3" (test name with attempt suffix)
    // NOTE: Does NOT match "[1]", "[2]" or "#1" - these are parameterized test invocations, NOT retries!
    private static final Pattern RETRY_PATTERN = Pattern.compile(
        "\\bAttempt\\s+(\\d+)", 
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
        M00nPlaywright.clear();  // Clean up Playwright registrations
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
     * 
     * IMPORTANT: Captures Playwright artifacts IMMEDIATELY on failure,
     * BEFORE re-throwing the exception. This ensures capture happens before
     * any @AfterEach or other extensions that might close the browser context.
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
            // Mark test as failed
            M00nStep.markFailed();
            
            // CAPTURE PLAYWRIGHT ARTIFACTS IMMEDIATELY - before any @AfterEach runs!
            // This is critical when customers have their own extensions that close the context
            capturePlaywrightArtifacts(testResult, extensionContext);
            
            // Test failed - report immediately (before JUnit Pioneer catches for retry)
            reportTestResult(extensionContext, "failed", t);
            throw t; // Re-throw so JUnit Pioneer can handle retry
        }
    }
    
    /**
     * Capture Playwright artifacts (screenshot, trace) immediately on failure.
     * 
     * First checks if test implements PlaywrightTestProvider for auto-registration,
     * then falls back to manual M00nPlaywright.setPage() registration.
     * 
     * This runs BEFORE @AfterEach, so the browser is still open.
     */
    private void capturePlaywrightArtifacts(TestResult testResult, ExtensionContext context) {
        if (testResult == null) return;
        
        String testId = testResult.getTestId();
        String testName = sanitizeFilename(context.getDisplayName());
        
        // Auto-register from PlaywrightTestProvider if test implements it
        // This allows customers to implement the interface instead of calling M00nPlaywright.setPage()
        autoRegisterFromProvider(context);
        
        // Capture screenshot first (before any state changes)
        if (M00nPlaywright.hasPage()) {
            log.debug("[M00nExtension] Capturing Playwright artifacts for: {}", testName);
            M00nPlaywright.captureScreenshot(testId);
            
            // Capture trace if context was registered
            if (M00nPlaywright.hasContext()) {
                M00nPlaywright.captureTrace(testId, testName);
            }
        }
    }
    
    /**
     * Auto-register Playwright objects for artifact capture.
     * 
     * Priority order:
     * 1. Manual registration via M00nPlaywright.setPage() - highest priority
     * 2. PlaywrightTestProvider interface - explicit contract
     * 3. Auto-detection via reflection - zero-config fallback
     * 
     * This enables ZERO code changes for customers - just add dependency + config!
     */
    private void autoRegisterFromProvider(ExtensionContext context) {
        // Already registered manually? Skip auto-detection
        if (M00nPlaywright.hasPage()) {
            return;
        }
        
        try {
            Object testInstance = context.getRequiredTestInstance();
            
            // Priority 1: Check if test implements PlaywrightTestProvider
            if (testInstance instanceof PlaywrightTestProvider provider) {
                Object page = provider.getPage();
                if (page != null) {
                    M00nPlaywright.setPage(page);
                    log.debug("[M00nExtension] Auto-registered Page from PlaywrightTestProvider");
                }
                
                if (!M00nPlaywright.hasContext()) {
                    Object browserContext = provider.getContext();
                    if (browserContext != null) {
                        M00nPlaywright.setContext(browserContext);
                        log.debug("[M00nExtension] Auto-registered Context from PlaywrightTestProvider");
                    }
                }
                return;
            }
            
            // Priority 2: Auto-detect Playwright fields via reflection (ZERO-CONFIG!)
            autoDetectPlaywrightFields(testInstance);
            
        } catch (Exception e) {
            log.debug("[M00nExtension] Could not auto-register Playwright: {}", e.getMessage());
        }
    }
    
    /**
     * Auto-detect Playwright Page and BrowserContext fields via reflection.
     * This enables ZERO code changes for customers migrating from other reporters.
     * 
     * Scans test instance (and parent classes) for fields of type:
     * - com.microsoft.playwright.Page
     * - com.microsoft.playwright.BrowserContext
     */
    private void autoDetectPlaywrightFields(Object testInstance) {
        Class<?> clazz = testInstance.getClass();
        
        // Scan class hierarchy (including parent classes)
        while (clazz != null && clazz != Object.class) {
            for (var field : clazz.getDeclaredFields()) {
                try {
                    // Handle Java 17+ module restrictions gracefully
                    try {
                        field.setAccessible(true);
                    } catch (RuntimeException e) {
                        // InaccessibleObjectException (Java 9+) or SecurityException
                        log.debug("[M00nExtension] Cannot access field {} (module restrictions): {}", 
                            field.getName(), e.getMessage());
                        continue;
                    }
                    
                    Object value = field.get(testInstance);
                    
                    if (value == null) continue;
                    
                    String typeName = value.getClass().getName();
                    
                    // Detect Playwright Page (including impl class)
                    if (!M00nPlaywright.hasPage() && isPlaywrightPage(value, typeName)) {
                        M00nPlaywright.setPage(value);
                        log.debug("[M00nExtension] Auto-detected Page field: {}.{}", 
                            clazz.getSimpleName(), field.getName());
                    }
                    
                    // Detect Playwright BrowserContext
                    if (!M00nPlaywright.hasContext() && isPlaywrightContext(value, typeName)) {
                        M00nPlaywright.setContext(value);
                        log.debug("[M00nExtension] Auto-detected BrowserContext field: {}.{}", 
                            clazz.getSimpleName(), field.getName());
                    }
                    
                } catch (Exception e) {
                    // Skip inaccessible fields
                    log.debug("[M00nExtension] Error accessing field {}: {}", field.getName(), e.getMessage());
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
    
    /**
     * Check if object is a Playwright Page (handles impl classes and wrappers).
     */
    private boolean isPlaywrightPage(Object obj, String typeName) {
        // Direct Page implementation
        if (typeName.contains("playwright") && typeName.contains("Page")) {
            return true;
        }
        
        // Check interfaces
        for (Class<?> iface : obj.getClass().getInterfaces()) {
            if (iface.getName().equals("com.microsoft.playwright.Page")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if object is a Playwright BrowserContext.
     */
    private boolean isPlaywrightContext(Object obj, String typeName) {
        // Direct BrowserContext implementation
        if (typeName.contains("playwright") && typeName.contains("BrowserContext")) {
            return true;
        }
        
        // Check interfaces
        for (Class<?> iface : obj.getClass().getInterfaces()) {
            if (iface.getName().equals("com.microsoft.playwright.BrowserContext")) {
                return true;
            }
        }
        
        return false;
    }
    
    private String sanitizeFilename(String name) {
        if (name == null) return "unknown";
        String sanitized = name.replaceAll("[^a-zA-Z0-9-_]", "_")
                               .replaceAll("_+", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 100));
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
     * Gets the test display name from @DisplayName on method or context display name.
     * For @RetryingTest, extracts the base name by stripping the attempt suffix.
     * For @ParameterizedTest, uses the full context display name (includes parameters).
     */
    private String getTestDisplayName(ExtensionContext context) {
        try {
            var method = context.getRequiredTestMethod();
            
            // Check for @DisplayName on method (highest priority)
            DisplayName displayName = method.getAnnotation(DisplayName.class);
            if (displayName != null && !displayName.value().isEmpty()) {
                return displayName.value();
            }
            
            // Use context.getDisplayName() for all other cases
            // This correctly handles:
            // - @ParameterizedTest: "1: Test name with params [value1] [value2]"
            // - @RepeatedTest: "repetition 1 of 5"
            // - Regular tests: method name or custom display name
            String contextDisplayName = context.getDisplayName();
            
            if (contextDisplayName != null && !contextDisplayName.isEmpty()) {
                // For @RetryingTest, strip the attempt suffix
                // e.g., "🔄 Flaky test - Attempt 1" -> "🔄 Flaky test"
                if (RETRY_PATTERN.matcher(contextDisplayName).find()) {
                    String baseName = RETRY_PATTERN.matcher(contextDisplayName)
                        .replaceAll("")
                        .replaceAll("\\s*[-–]\\s*$", "")  // Remove trailing dashes
                        .trim();
                    if (!baseName.isEmpty()) {
                        return baseName;
                    }
                }
                
                // Return context display name as-is (includes parameterized test info)
                return contextDisplayName;
            }
            
            // Ultimate fallback to method name (humanized)
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
     * Only matches JUnit Pioneer's @RetryingTest "Attempt N" pattern.
     * Does NOT match parameterized test patterns like "[1]" or "#1".
     */
    private RetryInfo parseRetryInfo(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return new RetryInfo("", 0);  // Empty string instead of null to avoid NPE
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
     * 
     * IMPORTANT: Only JUnit Pioneer's @RetryingTest creates actual retries.
     * Other test templates (@ParameterizedTest, @RepeatedTest) use invocation numbers
     * that are NOT retries and should NOT be counted.
     * 
     * JUnit Pioneer's @RetryingTest default display name includes "Attempt N":
     * - "Attempt 1", "Attempt 2", etc.
     * - Or custom: "My Test - Attempt 3"
     * 
     * We ONLY detect retries from the explicit "Attempt N" pattern in display names.
     */
    private int getRetryAttemptFromContext(ExtensionContext context) {
        String displayName = context.getDisplayName();
        
        log.debug("[M00nExtension] getRetryAttemptFromContext - displayName='{}'", displayName);
        
        // Only parse "Attempt N" from display name - this is JUnit Pioneer's @RetryingTest pattern
        // Do NOT use unique ID patterns like [test-template-invocation:#N] - those are 
        // sequential invocation counters for ALL test templates, not retry indicators!
        RetryInfo info = parseRetryInfo(displayName);
        if (info.attempt > 0) {
            log.debug("[M00nExtension] Found retry attempt {} from displayName", info.attempt);
            return info.attempt;
        }
        
        log.debug("[M00nExtension] No retry attempt detected (not @RetryingTest), returning 0");
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
