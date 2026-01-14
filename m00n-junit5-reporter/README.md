# M00n JUnit 5 Reporter

[![Maven Central](https://img.shields.io/maven-central/v/io.m00nreport/m00n-junit5-reporter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:io.m00nreport%20a:m00n-junit5-reporter)
[![JitPack](https://jitpack.io/v/m00nreport/m00n-java-reporter.svg)](https://jitpack.io/#m00nreport/m00n-java-reporter)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A powerful JUnit 5 reporter for [M00n Report](https://app.m00n.report) that sends test results in real-time to your dashboard.

## ✨ Features

- 🚀 **Real-time streaming** - See tests as they run via WebSocket
- 📝 **Automatic step tracking** - Use `@Step` annotation with Page Objects
- 🔄 **Retry tracking** - Full support for `@RetryingTest` with attempt history
- 📸 **Rich attachments** - Screenshots, videos, traces, logs
- 🏷️ **Display names** - Use `@DisplayName` for readable test titles
- ⚡ **Parallel execution** - Thread-safe for concurrent tests
- 🎭 **Playwright ready** - Built-in integration for browser testing

---

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    // For pre-release versions:
    // maven("https://jitpack.io")
}

dependencies {
    // M00n Reporter
    testImplementation("io.m00nreport:m00n-junit5-reporter:1.0.0")
    
    // JUnit 5
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // JUnit Pioneer for @RetryingTest (optional)
    testImplementation("org.junit-pioneer:junit-pioneer:2.3.0")
    
    // Playwright (optional)
    implementation("com.microsoft.playwright:playwright:1.57.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")
    
    // Assertions (optional)
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.test {
    useJUnitPlatform()
    
    // Run tests sequentially for consistent M00n reporting
    maxParallelForks = 1
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.m00nreport</groupId>
        <artifactId>m00n-junit5-reporter</artifactId>
        <version>1.0.0</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.11.3</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>com.microsoft.playwright</groupId>
        <artifactId>playwright</artifactId>
        <version>1.57.0</version>
    </dependency>
</dependencies>
```

---

## ⚙️ Configuration

### 1. Create `src/test/resources/m00n.properties`

```properties
# Required: M00n Report server URL
m00n.serverUrl=https://app.m00n.report

# Required: Project API key (get from M00n Report dashboard)
m00n.apiKey=m00n_your_project_api_key

# Optional: Run/launch name
m00n.launch=My Playwright Tests

# Optional: Tags (comma-separated)
m00n.tags=smoke,regression

# Optional: Debug mode
m00n.debug=false

# Optional: Custom attributes
m00n.attribute.environment=staging
m00n.attribute.browser=chromium
```

### 2. Enable Extension Auto-Detection

Create `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

### 3. Environment Variables (Alternative)

```bash
export M00N_SERVER_URL=https://app.m00n.report
export M00N_API_KEY=m00n_your_project_api_key
export M00N_LAUNCH="CI Build #${BUILD_NUMBER}"
export M00N_TAGS="ci,${BRANCH_NAME}"
```

---

## 🏗️ Project Structure (Recommended)

```
src/test/
├── java/com/example/
│   ├── tests/                    # Test classes
│   │   ├── BasePlaywrightTest.java
│   │   ├── LoginTests.java
│   │   └── CheckoutTests.java
│   └── pages/                    # Page Objects with @Step
│       ├── PageFactory.java
│       ├── LoginPage.java
│       └── CheckoutPage.java
└── resources/
    ├── m00n.properties           # M00n configuration
    ├── junit-platform.properties # JUnit configuration
    └── logback-test.xml          # Logging configuration
```

---

## 🎭 Playwright Integration with Step Tracking

### Step 1: Create Page Object Interfaces with `@Step`

```java
package com.example.pages;

import com.m00nreport.reporter.annotations.Step;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;

/**
 * Page Object interface for the Login page.
 * Methods annotated with @Step are automatically tracked as test steps.
 */
public interface LoginPage {

    @Step("Open login page")
    void open();

    @Step("Enter username '{username}'")
    void enterUsername(String username);

    @Step("Enter password")
    void enterPassword(String password);

    @Step("Click login button")
    void clickLogin();

    @Step("Verify login successful")
    boolean isLoggedIn();

    @Step("Get error message")
    String getErrorMessage();

    // =========================================================================
    // Implementation (nested class)
    // =========================================================================
    
    class Impl implements LoginPage {
        private final Page page;
        private final Locator usernameInput;
        private final Locator passwordInput;
        private final Locator loginButton;
        private final Locator errorMessage;
        private final Locator welcomeMessage;

        public Impl(Page page) {
            this.page = page;
            this.usernameInput = page.locator("#username");
            this.passwordInput = page.locator("#password");
            this.loginButton = page.locator("button[type='submit']");
            this.errorMessage = page.locator(".error-message");
            this.welcomeMessage = page.locator(".welcome");
        }

        @Override
        public void open() {
            page.navigate("https://example.com/login");
            page.waitForLoadState();
        }

        @Override
        public void enterUsername(String username) {
            usernameInput.fill(username);
        }

        @Override
        public void enterPassword(String password) {
            passwordInput.fill(password);
        }

        @Override
        public void clickLogin() {
            loginButton.click();
            page.waitForLoadState();
        }

        @Override
        public boolean isLoggedIn() {
            return welcomeMessage.isVisible();
        }

        @Override
        public String getErrorMessage() {
            return errorMessage.textContent();
        }
    }
}
```

### Step 2: Create PageFactory with StepProxy

```java
package com.example.pages;

import com.m00nreport.reporter.StepProxy;
import com.microsoft.playwright.Page;

/**
 * Factory for creating page objects with automatic step tracking.
 * All @Step annotated methods are tracked and reported to M00n Report.
 */
public final class PageFactory {

    private PageFactory() {} // Utility class

    public static LoginPage createLoginPage(Page page) {
        return StepProxy.create(LoginPage.class, new LoginPage.Impl(page));
    }

    public static CheckoutPage createCheckoutPage(Page page) {
        return StepProxy.create(CheckoutPage.class, new CheckoutPage.Impl(page));
    }
    
    // Add more page factory methods as needed...
}
```

### Step 3: Create Base Test Class

```java
package com.example.tests;

import com.m00nreport.reporter.M00nReporter;
import com.m00nreport.reporter.M00nStep;
import com.m00nreport.reporter.model.AttachmentData;
import com.m00nreport.reporter.model.TestResult;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for all Playwright tests.
 * 
 * Provides:
 * - Browser lifecycle management (shared browser instance)
 * - Context and page setup per test with video/tracing
 * - Automatic artifact capture on failure (screenshot, trace, video)
 * - Integration with M00n Reporter for test tracking
 */
public abstract class BasePlaywrightTest {

    // Shared browser instance (created once per test class)
    private static Playwright playwright;
    private static Browser browser;
    
    // Per-test instances
    protected BrowserContext context;
    protected Page page;
    
    // Artifacts directory
    protected static final Path ARTIFACTS_DIR = Paths.get("test-results");
    
    // Test tracking
    private String currentTestId;
    private String currentTestName;

    // =========================================================================
    // Lifecycle: Class Level
    // =========================================================================

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(true));
        
        // Create artifacts directories
        try {
            Files.createDirectories(ARTIFACTS_DIR.resolve("traces"));
            Files.createDirectories(ARTIFACTS_DIR.resolve("videos"));
            Files.createDirectories(ARTIFACTS_DIR.resolve("screenshots"));
        } catch (Exception e) {
            System.err.println("[BaseTest] Failed to create artifacts dirs: " + e.getMessage());
        }
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    // =========================================================================
    // Lifecycle: Test Level
    // =========================================================================

    @BeforeEach
    void setUp(TestInfo testInfo) {
        // Create context with video recording
        context = browser.newContext(new Browser.NewContextOptions()
            .setViewportSize(1280, 720)
            .setRecordVideoDir(ARTIFACTS_DIR.resolve("videos"))
            .setRecordVideoSize(1280, 720));
        
        page = context.newPage();
        
        // Start tracing for debugging
        context.tracing().start(new Tracing.StartOptions()
            .setScreenshots(true)
            .setSnapshots(true)
            .setSources(true));
        
        // Store test info for artifact capture
        this.currentTestName = sanitizeFilename(testInfo.getDisplayName());
        this.currentTestId = M00nStep.current()
            .map(TestResult::getTestId)
            .orElse(null);
    }

    @AfterEach
    void tearDown() {
        final String testId = this.currentTestId;
        final boolean testFailed = M00nStep.isCurrentTestFailed();
        
        if (testFailed && testId != null) {
            captureFailureArtifacts(testId);
        } else {
            stopTracingQuietly();
        }
        
        closeContextQuietly();
    }

    // =========================================================================
    // Artifact Capture
    // =========================================================================

    private void captureFailureArtifacts(String testId) {
        System.out.println("[M00nReporter] Capturing artifacts for failed test: " + testId);
        
        // 1. Screenshot (before closing anything)
        captureScreenshot(testId);
        
        // 2. Trace
        captureTrace(testId);
        
        // 3. Video (after closing context)
        Path videoPath = getVideoPath();
        closeContextQuietly();
        captureVideo(testId, videoPath);
    }

    private void captureScreenshot(String testId) {
        if (page == null) return;
        try {
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            M00nReporter.getInstance().attachToTest(testId,
                AttachmentData.screenshot("failure-screenshot.png", screenshot));
            System.out.println("[M00nReporter] ✓ Screenshot attached");
        } catch (Exception e) {
            System.err.println("[M00nReporter] ✗ Screenshot failed: " + e.getMessage());
        }
    }

    private void captureTrace(String testId) {
        if (context == null) return;
        Path tracePath = ARTIFACTS_DIR.resolve("traces").resolve(currentTestName + "-trace.zip");
        try {
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
            if (Files.exists(tracePath)) {
                byte[] traceBytes = Files.readAllBytes(tracePath);
                M00nReporter.getInstance().attachToTest(testId,
                    AttachmentData.trace("trace.zip", traceBytes));
                System.out.println("[M00nReporter] ✓ Trace attached");
            }
        } catch (Exception e) {
            System.err.println("[M00nReporter] ✗ Trace failed: " + e.getMessage());
        }
    }

    private Path getVideoPath() {
        if (page == null || page.video() == null) return null;
        try {
            return page.video().path();
        } catch (Exception e) {
            return null;
        }
    }

    private void captureVideo(String testId, Path videoPath) {
        if (videoPath == null || !Files.exists(videoPath)) return;
        try {
            Thread.sleep(300); // Wait for video to finalize
            byte[] videoBytes = Files.readAllBytes(videoPath);
            M00nReporter.getInstance().attachToTest(testId,
                AttachmentData.video("video.webm", videoBytes));
            System.out.println("[M00nReporter] ✓ Video attached");
        } catch (Exception e) {
            System.err.println("[M00nReporter] ✗ Video failed: " + e.getMessage());
        }
    }

    private void stopTracingQuietly() {
        if (context == null) return;
        try {
            Path tracePath = ARTIFACTS_DIR.resolve("traces").resolve(currentTestName + "-trace.zip");
            context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
        } catch (Exception ignored) {}
    }

    private void closeContextQuietly() {
        if (context != null) {
            try { context.close(); } catch (Exception ignored) {}
            context = null;
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private String sanitizeFilename(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9-_]", "_")
                               .replaceAll("_+", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 100));
    }

    protected void navigateTo(String url) {
        page.navigate(url, new Page.NavigateOptions()
            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }
}
```

### Step 4: Write Your Tests

```java
package com.example.tests;

import com.example.pages.LoginPage;
import com.example.pages.PageFactory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Login tests using Page Object pattern with automatic step tracking.
 */
@DisplayName("Login Tests")
class LoginTests extends BasePlaywrightTest {

    private LoginPage loginPage;

    @BeforeEach
    void setUpPage(TestInfo testInfo) {
        // Create page object with step tracking
        loginPage = PageFactory.createLoginPage(page);
    }

    @Test
    @DisplayName("✅ Should login with valid credentials")
    void testValidLogin() {
        // Each method call is automatically tracked as a step!
        loginPage.open();
        loginPage.enterUsername("testuser");
        loginPage.enterPassword("password123");
        loginPage.clickLogin();
        
        assertTrue(loginPage.isLoggedIn(), "User should be logged in");
    }

    @Test
    @DisplayName("❌ Should reject invalid password")
    void testInvalidPassword() {
        loginPage.open();
        loginPage.enterUsername("testuser");
        loginPage.enterPassword("wrongpassword");
        loginPage.clickLogin();
        
        assertFalse(loginPage.isLoggedIn());
        assertEquals("Invalid credentials", loginPage.getErrorMessage());
    }

    @Test
    @Disabled("Feature not implemented")
    @DisplayName("⏭️ Should support two-factor auth")
    void testTwoFactorAuth() {
        // This test is skipped
    }
}
```

---

## 📝 Step Tracking Options

### Option 1: Page Objects with `@Step` (Recommended)

Use `StepProxy.create()` to wrap page objects - all `@Step` annotated methods are automatically tracked.

```java
// Interface with @Step annotations
public interface MyPage {
    @Step("Navigate to page")
    void open();
    
    @Step  // Auto-generates title from method name
    void clickSubmitButton();
}

// Create proxied instance
MyPage page = StepProxy.create(MyPage.class, new MyPageImpl(playwrightPage));
page.open();  // Automatically tracked!
```

### Option 2: Manual Steps

For tests without page objects:

```java
@Test
void testManualSteps() {
    M00nStep.current().ifPresent(test -> {
        var step1 = test.addStep("Step 1: Setup", "setup");
        // ... do setup ...
        test.completeStep(step1, true, null);
        
        var step2 = test.addStep("Step 2: Action", "action");
        // ... do action ...
        test.completeStep(step2, true, null);
    });
}
```

---

## 🔄 Retry Support

Use JUnit Pioneer's `@RetryingTest` for flaky tests:

```java
import org.junitpioneer.jupiter.RetryingTest;

@RetryingTest(maxAttempts = 3, name = "🔄 Flaky API call - Attempt {index}")
void flakyApiTest() {
    // M00n Report tracks each attempt separately
    // and marks the test as "flaky" if it passes on retry
    var response = callUnstableApi();
    assertEquals(200, response.status());
}
```

---

## 📎 Attachment Types

```java
import com.m00nreport.reporter.model.AttachmentData;

// Screenshot
AttachmentData.screenshot("failure.png", screenshotBytes);

// Video
AttachmentData.video("recording.webm", videoBytes);

// Trace (Playwright)
AttachmentData.trace("trace.zip", traceBytes);

// Log file
AttachmentData.log("console.log", logBytes);

// Generic file
AttachmentData.file("data.json", jsonBytes, "application/json");
```

---

## 📊 Configuration Reference

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `m00n.serverUrl` | `M00N_SERVER_URL` | - | Server URL (required) |
| `m00n.apiKey` | `M00N_API_KEY` | - | Project API key (required) |
| `m00n.enabled` | `M00N_ENABLED` | `true` | Enable/disable reporter |
| `m00n.launch` | `M00N_LAUNCH` | `Java Tests` | Run/launch name |
| `m00n.tags` | `M00N_TAGS` | - | Comma-separated tags |
| `m00n.debug` | `M00N_DEBUG` | `false` | Enable debug logging |
| `m00n.timeout` | `M00N_TIMEOUT` | `30000` | HTTP timeout (ms) |
| `m00n.maxRetries` | `M00N_MAX_RETRIES` | `3` | Max retry attempts |
| `m00n.attribute.*` | - | - | Custom attributes |

---

## 🔧 Logback Configuration

Create `src/test/resources/logback-test.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %highlight(%-5level) %cyan([%logger{20}]) - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- M00n Reporter - show INFO and above -->
    <logger name="com.m00nreport.reporter" level="INFO" />
    
    <!-- Playwright - reduce noise -->
    <logger name="com.microsoft.playwright" level="WARN" />
    
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

---

## 🚀 Running Tests

### From Command Line

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.example.tests.LoginTests"

# Run with custom configuration
./gradlew test -Dm00n.launch="CI Build #123" -Dm00n.tags="smoke"

# Run in headed mode (for debugging)
./gradlew test -Dplaywright.headless=false
```

### From IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Right-click on a test class or method
3. Select "Run 'TestName'"
4. Results stream to M00n Report in real-time!

---

## 📝 License

MIT License - See [LICENSE](LICENSE) for details.

## 🔗 Links

- [M00n Report Dashboard](https://app.m00n.report)
- [Documentation](https://docs.m00n.report)
- [Issue Tracker](https://github.com/m00nsolutions/m00nreport/issues)
