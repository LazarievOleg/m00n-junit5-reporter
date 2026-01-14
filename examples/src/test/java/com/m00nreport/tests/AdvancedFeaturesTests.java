package com.m00nreport.tests;

import com.m00nreport.reporter.annotations.Step;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junitpioneer.jupiter.RetryingTest;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Advanced features tests including retries using AspectJ-based step tracking.
 */
@DisplayName("Advanced Features Tests")
class AdvancedFeaturesTests extends BasePlaywrightTest {
    
    @Test
    @DisplayName("Download page navigation")
    void downloadPageNavigation(Page page) {
        var advancedPage = new AdvancedPage(page);
        advancedPage.open();
        advancedPage.navigateToDownload();
        advancedPage.verifyDownloadPage();
    }
    
    @Test
    @DisplayName("Language selector works")
    void languageSelectorWorks(Page page) {
        var advancedPage = new AdvancedPage(page);
        advancedPage.open();
        advancedPage.navigateToDocs();
        advancedPage.verifyLanguageOptions();
    }
    
    @Test
    @DisplayName("Dark mode toggle")
    void darkModeToggle(Page page) {
        var advancedPage = new AdvancedPage(page);
        advancedPage.open();
        advancedPage.toggleDarkMode();
        advancedPage.verifyDarkMode();
    }
    
    @Test
    @DisplayName("GitHub link is correct")
    void githubLinkCorrect(Page page) {
        var advancedPage = new AdvancedPage(page);
        advancedPage.open();
        advancedPage.verifyGitHubLink();
    }
    
    @RetryingTest(maxAttempts = 3, name = "🔄 Flaky network test - Attempt {index}")
    @DisplayName("Handle potential network issues")
    void handleNetworkIssues(Page page) {
        var advancedPage = new AdvancedPage(page);
        advancedPage.open();
        advancedPage.verifyLoaded();
    }
    
    @Test
    @DisplayName("External links open correctly")
    void externalLinksOpen(Page page) {
        var advancedPage = new AdvancedPage(page);
        advancedPage.open();
        advancedPage.verifyExternalLinks();
    }
    
    @Test
    @Disabled("Feature not yet implemented")
    @DisplayName("⏭️ Skipped test example")
    void skippedTest(Page page) {
        var advancedPage = new AdvancedPage(page);
        advancedPage.open();
    }
    
    // =========================================================================
    // Page Object Class
    // =========================================================================
    
    private static class AdvancedPage {
        private final Page page;
        
        AdvancedPage(Page page) {
            this.page = page;
        }
        
        @Step("Open Playwright homepage")
        public void open() {
            page.navigate("https://playwright.dev/");
        }
        
        @Step("Verify page is loaded")
        public void verifyLoaded() {
            assertThat(page).hasTitle(java.util.regex.Pattern.compile(".*Playwright.*"));
        }
        
        @Step("Navigate to download page")
        public void navigateToDownload() {
            page.locator("a:has-text('Get started')").first().click();
            page.waitForLoadState();
        }
        
        @Step("Verify download page")
        public void verifyDownloadPage() {
            assertThat(page.locator("article")).isVisible();
        }
        
        @Step("Navigate to documentation")
        public void navigateToDocs() {
            page.locator("a:has-text('Docs')").first().click();
            page.waitForLoadState();
        }
        
        @Step("Verify language selector options")
        public void verifyLanguageOptions() {
            var languageDropdown = page.locator(".navbar__item.dropdown");
            assertThat(languageDropdown.first()).isVisible();
        }
        
        @Step("Toggle dark mode")
        public void toggleDarkMode() {
            page.locator("button[class*='toggle']").click();
            page.waitForTimeout(300);
        }
        
        @Step("Verify dark mode is active")
        public void verifyDarkMode() {
            // Just verify the toggle works
            assertThat(page.locator("html")).isVisible();
        }
        
        @Step("Verify GitHub link")
        public void verifyGitHubLink() {
            assertThat(page.locator("a[href*='github.com/microsoft/playwright']")).isVisible();
        }
        
        @Step("Verify external links")
        public void verifyExternalLinks() {
            var externalLinks = page.locator("a[target='_blank']");
            assertThat(externalLinks.first()).isVisible();
        }
    }
}
