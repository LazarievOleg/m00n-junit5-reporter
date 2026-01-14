package com.m00nreport.reporter;

import com.m00nreport.reporter.model.AttachmentData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Integration point for Playwright tests with M00n Report.
 * 
 * <p>Enables automatic screenshot/trace capture on test failure.
 * Works with ANY custom base test class or extension - no inheritance required!</p>
 * 
 * <h2>Quick Setup (Screenshots):</h2>
 * <pre>{@code
 * // No @ExtendWith needed - M00nExtension is auto-registered!
 * class MyTests {
 *     @BeforeEach
 *     void setUp() {
 *         page = context.newPage();
 *         M00nPlaywright.setPage(page);  // That's it! Screenshots on failure
 *     }
 * }
 * }</pre>
 * 
 * <h2>With Traces:</h2>
 * <pre>{@code
 * class MyTests {
 *     @BeforeEach
 *     void setUp() {
 *         context = browser.newContext();
 *         context.tracing().start(new Tracing.StartOptions()
 *             .setScreenshots(true).setSnapshots(true));
 *         page = context.newPage();
 *         
 *         M00nPlaywright.setPage(page);
 *         M00nPlaywright.setContext(context);  // Enables trace capture
 *     }
 * }
 * }</pre>
 * 
 * <h2>With Videos (requires extension):</h2>
 * <pre>{@code
 * @ExtendWith(M00nPlaywrightExtension.class)  // Only for video capture
 * class MyTests {
 *     @BeforeEach
 *     void setUp() {
 *         context = browser.newContext(new Browser.NewContextOptions()
 *             .setRecordVideoDir(Paths.get("videos")));
 *         page = context.newPage();
 *         
 *         M00nPlaywright.setPage(page);
 *         M00nPlaywright.setContext(context);
 *     }
 *     
 *     @AfterEach
 *     void tearDown() {
 *         context.close();  // Video captured after close
 *     }
 * }
 * }</pre>
 * 
 * <h2>Works With Custom Extensions:</h2>
 * <p>Since artifacts are captured IMMEDIATELY when the test fails (before @AfterEach),
 * it works seamlessly with custom extensions that close the browser in teardown.</p>
 * 
 * @see M00nPlaywrightExtension
 */
public final class M00nPlaywright {
    
    private static final Logger log = LoggerFactory.getLogger(M00nPlaywright.class);
    
    // Configuration
    private static final int VIDEO_FINALIZE_TIMEOUT_MS = 2000;
    private static final int VIDEO_POLL_INTERVAL_MS = 100;
    
    // Thread-local storage for Playwright objects
    private static final ThreadLocal<Object> CURRENT_PAGE = new ThreadLocal<>();
    private static final ThreadLocal<Object> CURRENT_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Path> TRACE_PATH = new ThreadLocal<>();
    private static final ThreadLocal<Supplier<Path>> VIDEO_PATH_SUPPLIER = new ThreadLocal<>();
    
    // Track if artifacts were already captured (prevents duplicates)
    private static final ThreadLocal<Boolean> SCREENSHOT_CAPTURED = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> TRACE_CAPTURED = ThreadLocal.withInitial(() -> false);
    
    private M00nPlaywright() {} // Utility class
    
    // =========================================================================
    // Registration API - Called by user's test code
    // =========================================================================
    
    /**
     * Register the current Playwright Page for automatic screenshot capture.
     * Call this in your @BeforeEach after creating the page.
     * 
     * @param page The Playwright Page object (com.microsoft.playwright.Page)
     */
    public static void setPage(Object page) {
        CURRENT_PAGE.set(page);
    }
    
    /**
     * Register the current BrowserContext for trace capture.
     * Call this if you want traces to be captured on failure.
     * 
     * @param context The Playwright BrowserContext (com.microsoft.playwright.BrowserContext)
     */
    public static void setContext(Object context) {
        CURRENT_CONTEXT.set(context);
    }
    
    /**
     * Set the path where trace should be saved.
     * If not set, a default path will be used.
     * 
     * @param path Path for trace file (should end with .zip)
     */
    public static void setTracePath(Path path) {
        TRACE_PATH.set(path);
    }
    
    /**
     * Set a supplier that returns the video path after context is closed.
     * This is needed because video path is only available after page.video().path().
     * 
     * @param supplier Supplier that returns the video path
     */
    public static void setVideoPathSupplier(Supplier<Path> supplier) {
        VIDEO_PATH_SUPPLIER.set(supplier);
    }
    
    /**
     * Clear all registrations. Called automatically by M00nExtension.
     */
    public static void clear() {
        CURRENT_PAGE.remove();
        CURRENT_CONTEXT.remove();
        TRACE_PATH.remove();
        VIDEO_PATH_SUPPLIER.remove();
        SCREENSHOT_CAPTURED.remove();
        TRACE_CAPTURED.remove();
    }
    
    // =========================================================================
    // Internal API - Used by M00nExtension
    // =========================================================================
    
    /**
     * Check if a page is registered.
     */
    static boolean hasPage() {
        return CURRENT_PAGE.get() != null;
    }
    
    /**
     * Check if a context is registered.
     */
    static boolean hasContext() {
        return CURRENT_CONTEXT.get() != null;
    }
    
    /**
     * Check if a screenshot was already captured for this test.
     * Useful for base test classes to avoid duplicate screenshots.
     * 
     * @return true if screenshot was already captured
     */
    public static boolean isScreenshotCaptured() {
        return SCREENSHOT_CAPTURED.get();
    }
    
    /**
     * Check if a trace was already captured for this test.
     * Useful for base test classes to avoid duplicate traces.
     * 
     * @return true if trace was already captured
     */
    public static boolean isTraceCaptured() {
        return TRACE_CAPTURED.get();
    }
    
    /**
     * Mark screenshot as captured (call this if you capture screenshots manually).
     * Prevents M00nExtension from capturing duplicate screenshots.
     */
    public static void markScreenshotCaptured() {
        SCREENSHOT_CAPTURED.set(true);
    }
    
    /**
     * Mark trace as captured (call this if you capture traces manually).
     * Prevents M00nExtension from capturing duplicate traces.
     */
    public static void markTraceCaptured() {
        TRACE_CAPTURED.set(true);
    }
    
    /**
     * Capture screenshot from registered page.
     * Uses reflection to avoid compile-time dependency on Playwright.
     * 
     * @param testId The test ID to attach screenshot to
     * @return true if screenshot was captured successfully
     */
    static boolean captureScreenshot(String testId) {
        // Prevent duplicate screenshots
        if (SCREENSHOT_CAPTURED.get()) {
            log.debug("[M00nPlaywright] Screenshot already captured for this test, skipping");
            return true;
        }
        
        Object page = CURRENT_PAGE.get();
        if (page == null) return false;
        
        try {
            // Use reflection to call page.screenshot()
            // This avoids requiring Playwright as a compile dependency
            var screenshotOptionsClass = Class.forName("com.microsoft.playwright.Page$ScreenshotOptions");
            var screenshotOptions = screenshotOptionsClass.getDeclaredConstructor().newInstance();
            
            // Set fullPage = true
            var setFullPageMethod = screenshotOptionsClass.getMethod("setFullPage", boolean.class);
            setFullPageMethod.invoke(screenshotOptions, true);
            
            // Call page.screenshot(options)
            var screenshotMethod = page.getClass().getMethod("screenshot", screenshotOptionsClass);
            byte[] screenshot = (byte[]) screenshotMethod.invoke(page, screenshotOptions);
            
            if (screenshot != null && screenshot.length > 0) {
                M00nReporter.getInstance().attachToTest(testId,
                    AttachmentData.screenshot("failure-screenshot.png", screenshot));
                log.info("[M00nPlaywright] ✓ Screenshot captured ({} KB)", screenshot.length / 1024);
                SCREENSHOT_CAPTURED.set(true);
                return true;
            }
        } catch (Exception e) {
            log.error("[M00nPlaywright] ✗ Screenshot failed: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Capture trace from registered context.
     * 
     * @param testId The test ID to attach trace to
     * @param testName Sanitized test name for file naming
     * @return true if trace was captured successfully
     */
    static boolean captureTrace(String testId, String testName) {
        // Prevent duplicate traces
        if (TRACE_CAPTURED.get()) {
            log.debug("[M00nPlaywright] Trace already captured for this test, skipping");
            return true;
        }
        
        Object context = CURRENT_CONTEXT.get();
        if (context == null) return false;
        
        try {
            // Get or create trace path
            Path tracePath = TRACE_PATH.get();
            if (tracePath == null) {
                tracePath = Path.of("test-results", "traces", testName + "-trace.zip");
                Files.createDirectories(tracePath.getParent());
            }
            
            // Use reflection to call context.tracing().stop(options)
            var tracingMethod = context.getClass().getMethod("tracing");
            Object tracing = tracingMethod.invoke(context);
            
            var stopOptionsClass = Class.forName("com.microsoft.playwright.Tracing$StopOptions");
            var stopOptions = stopOptionsClass.getDeclaredConstructor().newInstance();
            var setPathMethod = stopOptionsClass.getMethod("setPath", Path.class);
            setPathMethod.invoke(stopOptions, tracePath);
            
            var stopMethod = tracing.getClass().getMethod("stop", stopOptionsClass);
            stopMethod.invoke(tracing, stopOptions);
            
            if (Files.exists(tracePath)) {
                byte[] traceBytes = Files.readAllBytes(tracePath);
                M00nReporter.getInstance().attachToTest(testId,
                    AttachmentData.trace("trace.zip", traceBytes));
                log.info("[M00nPlaywright] ✓ Trace captured ({} KB)", traceBytes.length / 1024);
                TRACE_CAPTURED.set(true);
                return true;
            }
        } catch (Exception e) {
            // Tracing might not have been started - this is OK, don't log as error
            String msg = e.getMessage();
            if (msg == null || !msg.contains("Tracing has not been started")) {
                log.error("[M00nPlaywright] ✗ Trace failed: {}", msg);
            }
        }
        return false;
    }
    
    /**
     * Capture video from registered context.
     * Must be called AFTER context is closed.
     * 
     * @param testId The test ID to attach video to
     * @return true if video was captured successfully
     */
    static boolean captureVideo(String testId) {
        Supplier<Path> supplier = VIDEO_PATH_SUPPLIER.get();
        if (supplier == null) {
            // Try to get video path from page
            Object page = CURRENT_PAGE.get();
            if (page != null) {
                try {
                    var videoMethod = page.getClass().getMethod("video");
                    Object video = videoMethod.invoke(page);
                    if (video != null) {
                        var pathMethod = video.getClass().getMethod("path");
                        Path videoPath = (Path) pathMethod.invoke(video);
                        if (videoPath != null) {
                            return captureVideoFromPath(testId, videoPath);
                        }
                    }
                } catch (Exception e) {
                    // Video might not be enabled - this is OK
                    log.debug("[M00nPlaywright] Video not available: {}", e.getMessage());
                }
            }
            return false;
        }
        
        try {
            Path videoPath = supplier.get();
            if (videoPath == null) {
                log.debug("[M00nPlaywright] Video path supplier returned null");
                return false;
            }
            return captureVideoFromPath(testId, videoPath);
        } catch (Exception e) {
            log.error("[M00nPlaywright] ✗ Video failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Capture video from path with polling for file readiness.
     * Videos may take a moment to finalize after context close.
     */
    private static boolean captureVideoFromPath(String testId, Path videoPath) {
        if (videoPath == null) {
            return false;
        }
        
        try {
            // Poll for video file to be ready (with timeout)
            long startTime = System.currentTimeMillis();
            long previousSize = -1;
            
            while (System.currentTimeMillis() - startTime < VIDEO_FINALIZE_TIMEOUT_MS) {
                if (!Files.exists(videoPath)) {
                    Thread.sleep(VIDEO_POLL_INTERVAL_MS);
                    continue;
                }
                
                long currentSize = Files.size(videoPath);
                
                // File exists and size is stable (same as last check) - it's ready
                if (currentSize > 0 && currentSize == previousSize) {
                    byte[] videoBytes = Files.readAllBytes(videoPath);
                    String filename = extractVideoFilename(videoPath);
                    M00nReporter.getInstance().attachToTest(testId,
                        AttachmentData.video(filename, videoBytes));
                    log.info("[M00nPlaywright] ✓ Video captured ({} KB)", videoBytes.length / 1024);
                    return true;
                }
                
                previousSize = currentSize;
                Thread.sleep(VIDEO_POLL_INTERVAL_MS);
            }
            
            // Timeout reached - try to read anyway if file exists
            if (Files.exists(videoPath) && Files.size(videoPath) > 0) {
                byte[] videoBytes = Files.readAllBytes(videoPath);
                String filename = extractVideoFilename(videoPath);
                M00nReporter.getInstance().attachToTest(testId,
                    AttachmentData.video(filename, videoBytes));
                log.warn("[M00nPlaywright] ✓ Video captured after timeout ({} KB)", videoBytes.length / 1024);
                return true;
            }
            
            log.warn("[M00nPlaywright] Video file not ready after {}ms timeout", VIDEO_FINALIZE_TIMEOUT_MS);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[M00nPlaywright] Video capture interrupted");
            return false;
        } catch (Exception e) {
            log.error("[M00nPlaywright] ✗ Video failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts video filename from path, with fallback to default.
     * 
     * @param videoPath The path to the video file
     * @return The filename (e.g., "test-video.webm") or default "video.webm"
     */
    private static String extractVideoFilename(Path videoPath) {
        if (videoPath == null || videoPath.getFileName() == null) {
            return "video.webm";
        }
        String filename = videoPath.getFileName().toString();
        return filename.isEmpty() ? "video.webm" : filename;
    }
    
    /**
     * Close context quietly (used internally).
     */
    static void closeContextQuietly() {
        Object context = CURRENT_CONTEXT.get();
        if (context == null) return;
        
        try {
            var closeMethod = context.getClass().getMethod("close");
            closeMethod.invoke(context);
        } catch (Exception e) {
            log.debug("[M00nPlaywright] Failed to close context: {}", e.getMessage());
        }
    }
}
