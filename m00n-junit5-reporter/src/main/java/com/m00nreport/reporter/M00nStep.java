package com.m00nreport.reporter;

import com.m00nreport.reporter.model.TestResult;

import java.util.Optional;

/**
 * Thread-local storage for the current test result.
 * 
 * <p>Used internally by the reporter to track which test is currently executing.
 * The {@link StepProxy} uses this to attach steps to the correct test.</p>
 */
public final class M00nStep {
    
    private static final ThreadLocal<TestResult> CURRENT_TEST = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> TEST_FAILED = new ThreadLocal<>();
    
    private M00nStep() {} // Utility class
    
    /**
     * Sets the current test result for the current thread.
     * Called automatically by M00nExtension.
     */
    public static void setCurrentTest(TestResult result) {
        CURRENT_TEST.set(result);
        TEST_FAILED.set(false); // Reset failure flag for new test
    }
    
    /**
     * Clears the current test result.
     */
    public static void clearCurrentTest() {
        CURRENT_TEST.remove();
        TEST_FAILED.remove();
    }
    
    /**
     * Gets the current test result, or null if not in a test context.
     */
    public static TestResult getCurrentTest() {
        return CURRENT_TEST.get();
    }
    
    /**
     * Gets the current test result as an Optional.
     */
    public static Optional<TestResult> current() {
        return Optional.ofNullable(CURRENT_TEST.get());
    }
    
    /**
     * Mark the current test as failed.
     * Used to trigger screenshot capture in @AfterEach.
     */
    public static void markFailed() {
        TEST_FAILED.set(true);
    }
    
    /**
     * Check if current test failed.
     */
    public static boolean isCurrentTestFailed() {
        return Boolean.TRUE.equals(TEST_FAILED.get());
    }
}
