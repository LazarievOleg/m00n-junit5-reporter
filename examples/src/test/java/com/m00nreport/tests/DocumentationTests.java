package com.m00nreport.tests;

import com.m00nreport.reporter.annotations.Step;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Documentation page tests using AspectJ-based step tracking.
 */
@DisplayName("Documentation Tests")
class DocumentationTests extends BasePlaywrightTest {
    
    @Test
    @DisplayName("Docs homepage loads")
    void docsHomepageLoads(Page page) {
        var docs = new DocsPage(page);
        docs.open();
        docs.verifyLoaded();
    }
    
    @Test
    @DisplayName("Navigation sidebar is visible")
    void navigationSidebarVisible(Page page) {
        var docs = new DocsPage(page);
        docs.open();
        docs.verifySidebar();
    }
    
    @Test
    @DisplayName("Table of contents is present")
    void tableOfContentsPresent(Page page) {
        var docs = new DocsPage(page);
        docs.open();
        docs.verifyTableOfContents();
    }
    
    @Test
    @DisplayName("Search functionality works")
    void searchWorks(Page page) {
        var docs = new DocsPage(page);
        docs.open();
        docs.search("locator");
        docs.verifySearchResults();
    }
    
    @Test
    @DisplayName("Code examples are highlighted")
    void codeExamplesHighlighted(Page page) {
        var docs = new DocsPage(page);
        docs.open();
        docs.navigateToGettingStarted();
        docs.verifyCodeHighlighting();
    }
    
    // =========================================================================
    // Page Object Class
    // =========================================================================
    
    private static class DocsPage {
        private final Page page;
        
        DocsPage(Page page) {
            this.page = page;
        }
        
        @Step("Open documentation page")
        public void open() {
            page.navigate("https://playwright.dev/java/docs/intro");
        }
        
        @Step("Verify docs page is loaded")
        public void verifyLoaded() {
            assertThat(page).hasTitle(java.util.regex.Pattern.compile(".*Playwright.*"));
        }
        
        @Step("Verify sidebar navigation")
        public void verifySidebar() {
            assertThat(page.locator(".theme-doc-sidebar-menu")).isVisible();
        }
        
        @Step("Verify table of contents")
        public void verifyTableOfContents() {
            assertThat(page.locator(".table-of-contents")).isVisible();
        }
        
        @Step("Search for query")
        public void search(String query) {
            page.locator("button.DocSearch").click();
            page.locator(".DocSearch-Input").fill(query);
            page.waitForTimeout(500);
        }
        
        @Step("Verify search results appear")
        public void verifySearchResults() {
            assertThat(page.locator(".DocSearch-Dropdown")).isVisible();
        }
        
        @Step("Navigate to Getting Started")
        public void navigateToGettingStarted() {
            page.locator("a:has-text('Getting started')").first().click();
            page.waitForLoadState();
        }
        
        @Step("Verify code syntax highlighting")
        public void verifyCodeHighlighting() {
            assertThat(page.locator("pre code")).first().isVisible();
        }
    }
}
