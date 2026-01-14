package com.m00nreport.tests;

import com.m00nreport.reporter.StepProxy;
import com.m00nreport.reporter.annotations.Step;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Documentation tests.
 */
@DisplayName("Playwright Documentation Tests")
class DocumentationTests extends BasePlaywrightTest {
    
    @Test
    @DisplayName("Introduction page loads")
    void introductionLoads(Page page) {
        var docs = StepProxy.create(DocsPage.class, new DocsPageImpl(page));
        docs.openIntroduction();
        docs.verifyPageLoaded();
        docs.verifySidebar();
    }
    
    @Test
    @DisplayName("Code examples are present")
    void codeExamples(Page page) {
        var docs = StepProxy.create(DocsPage.class, new DocsPageImpl(page));
        docs.openIntroduction();
        docs.verifyCodeBlocks();
    }
    
    @Test
    @DisplayName("Sidebar navigation works")
    void sidebarNavigation(Page page) {
        var docs = StepProxy.create(DocsPage.class, new DocsPageImpl(page));
        docs.openIntroduction();
        docs.clickWritingTests();
        docs.verifyWritingTestsPage();
    }
    
    @Test
    @DisplayName("Search functionality")
    void searchFunctionality(Page page) {
        var docs = StepProxy.create(DocsPage.class, new DocsPageImpl(page));
        docs.openIntroduction();
        docs.openSearch();
        docs.verifySearchModal();
        docs.closeSearch();
    }
    
    @ParameterizedTest(name = "Page {0} loads")
    @ValueSource(strings = {"/docs/intro", "/docs/writing-tests", "/docs/running-tests"})
    @DisplayName("Documentation pages load")
    void documentationPages(String path, Page page) {
        var docs = StepProxy.create(DocsPage.class, new DocsPageImpl(page));
        docs.openPage(path);
        docs.verifyPageLoaded();
    }
    
    // =========================================================================
    // Page Object Interface
    // =========================================================================
    
    public interface DocsPage {
        @Step("Open introduction page")
        void openIntroduction();
        
        @Step("Open documentation page")
        void openPage(String path);
        
        @Step("Verify page loaded")
        void verifyPageLoaded();
        
        @Step("Verify sidebar")
        void verifySidebar();
        
        @Step("Verify code blocks")
        void verifyCodeBlocks();
        
        @Step("Click Writing Tests link")
        void clickWritingTests();
        
        @Step("Verify Writing Tests page")
        void verifyWritingTestsPage();
        
        @Step("Open search")
        void openSearch();
        
        @Step("Verify search modal")
        void verifySearchModal();
        
        @Step("Close search")
        void closeSearch();
    }
    
    // =========================================================================
    // Implementation
    // =========================================================================
    
    public static class DocsPageImpl implements DocsPage {
        private final Page page;
        
        public DocsPageImpl(Page page) {
            this.page = page;
        }
        
        @Override
        public void openIntroduction() {
            page.navigate("https://playwright.dev/docs/intro");
        }
        
        @Override
        public void openPage(String path) {
            page.navigate("https://playwright.dev" + path);
        }
        
        @Override
        public void verifyPageLoaded() {
            assertThat(page.locator("article")).isVisible();
        }
        
        @Override
        public void verifySidebar() {
            assertThat(page.locator(".theme-doc-sidebar-menu")).isVisible();
        }
        
        @Override
        public void verifyCodeBlocks() {
            assertThat(page.locator("pre code").first()).isVisible();
        }
        
        @Override
        public void clickWritingTests() {
            page.locator("a[href='/docs/writing-tests']").click();
        }
        
        @Override
        public void verifyWritingTestsPage() {
            assertThat(page).hasURL(java.util.regex.Pattern.compile(".*writing-tests.*"));
        }
        
        @Override
        public void openSearch() {
            page.locator("button.DocSearch").click();
        }
        
        @Override
        public void verifySearchModal() {
            assertThat(page.locator(".DocSearch-Modal")).isVisible();
        }
        
        @Override
        public void closeSearch() {
            page.keyboard().press("Escape");
        }
    }
}
