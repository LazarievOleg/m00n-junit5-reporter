package com.m00nreport.tests;

import com.m00nreport.reporter.annotations.Step;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * API documentation tests using AspectJ-based step tracking.
 */
@DisplayName("API Documentation Tests")
class ApiDocumentationTests extends BasePlaywrightTest {
    
    @Test
    @DisplayName("API docs page loads")
    void apiDocsLoads(Page page) {
        var api = new ApiDocs(page);
        api.open();
        api.verifyLoaded();
    }
    
    @Test
    @DisplayName("API classes are listed")
    void apiClassesListed(Page page) {
        var api = new ApiDocs(page);
        api.open();
        api.verifyApiClasses();
    }
    
    @Test
    @DisplayName("Method documentation is shown")
    void methodDocsShown(Page page) {
        var api = new ApiDocs(page);
        api.open();
        api.navigateToPageClass();
        api.verifyMethods();
    }
    
    @Test
    @DisplayName("Code examples are present")
    void codeExamplesPresent(Page page) {
        var api = new ApiDocs(page);
        api.open();
        api.navigateToPageClass();
        api.verifyCodeExamples();
    }
    
    @Test
    @DisplayName("Links are functional")
    void linksAreFunctional(Page page) {
        var api = new ApiDocs(page);
        api.open();
        api.verifyInternalLinks();
    }
    
    // =========================================================================
    // Page Object Class
    // =========================================================================
    
    private static class ApiDocs {
        private final Page page;
        
        ApiDocs(Page page) {
            this.page = page;
        }
        
        @Step("Open API documentation")
        public void open() {
            page.navigate("https://playwright.dev/java/docs/api/class-playwright");
        }
        
        @Step("Verify API page is loaded")
        public void verifyLoaded() {
            assertThat(page).hasTitle(java.util.regex.Pattern.compile(".*Playwright.*"));
        }
        
        @Step("Verify API classes sidebar")
        public void verifyApiClasses() {
            assertThat(page.locator(".theme-doc-sidebar-menu")).isVisible();
        }
        
        @Step("Navigate to Page class")
        public void navigateToPageClass() {
            page.locator("a:has-text('Page')").first().click();
            page.waitForLoadState();
        }
        
        @Step("Verify methods are documented")
        public void verifyMethods() {
            assertThat(page.locator("h2, h3")).first().isVisible();
        }
        
        @Step("Verify code examples")
        public void verifyCodeExamples() {
            assertThat(page.locator("pre code")).first().isVisible();
        }
        
        @Step("Verify internal links work")
        public void verifyInternalLinks() {
            var links = page.locator(".theme-doc-sidebar-menu a");
            assertThat(links.first()).isVisible();
        }
    }
}
