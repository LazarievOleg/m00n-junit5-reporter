package com.m00nreport.tests;

import com.m00nreport.reporter.StepProxy;
import com.m00nreport.reporter.annotations.Step;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * API documentation tests.
 */
@DisplayName("API Documentation Tests")
class ApiDocumentationTests extends BasePlaywrightTest {
    
    @Test
    @DisplayName("Playwright class documentation")
    void playwrightClass(Page page) {
        var api = StepProxy.create(ApiDocs.class, new ApiDocsImpl(page));
        api.openPlaywrightClass();
        api.verifyPageLoaded();
        api.verifyHeading("Playwright");
    }
    
    @Test
    @DisplayName("Page class documentation")
    void pageClass(Page page) {
        var api = StepProxy.create(ApiDocs.class, new ApiDocsImpl(page));
        api.openPageClass();
        api.verifyPageLoaded();
        api.verifyHeading("Page");
    }
    
    @Test
    @DisplayName("Locator class documentation")
    void locatorClass(Page page) {
        var api = StepProxy.create(ApiDocs.class, new ApiDocsImpl(page));
        api.openLocatorClass();
        api.verifyPageLoaded();
        api.verifyHeading("Locator");
    }
    
    @Test
    @DisplayName("API sidebar navigation")
    void sidebarNavigation(Page page) {
        var api = StepProxy.create(ApiDocs.class, new ApiDocsImpl(page));
        api.openPlaywrightClass();
        api.verifySidebar();
        api.clickBrowserClass();
        api.verifyBrowserClassPage();
    }
    
    @ParameterizedTest(name = "API class {0}")
    @ValueSource(strings = {"class-playwright", "class-browser", "class-page", "class-locator"})
    @DisplayName("Core API classes exist")
    void coreApiClasses(String className, Page page) {
        var api = StepProxy.create(ApiDocs.class, new ApiDocsImpl(page));
        api.openApiClass(className);
        api.verifyPageLoaded();
    }
    
    // =========================================================================
    // Page Object Interface
    // =========================================================================
    
    public interface ApiDocs {
        @Step("Open Playwright class docs")
        void openPlaywrightClass();
        
        @Step("Open Page class docs")
        void openPageClass();
        
        @Step("Open Locator class docs")
        void openLocatorClass();
        
        @Step("Open API class")
        void openApiClass(String className);
        
        @Step("Verify page loaded")
        void verifyPageLoaded();
        
        @Step("Verify heading")
        void verifyHeading(String text);
        
        @Step("Verify sidebar")
        void verifySidebar();
        
        @Step("Click Browser class link")
        void clickBrowserClass();
        
        @Step("Verify Browser class page")
        void verifyBrowserClassPage();
    }
    
    // =========================================================================
    // Implementation
    // =========================================================================
    
    public static class ApiDocsImpl implements ApiDocs {
        private final Page page;
        
        public ApiDocsImpl(Page page) {
            this.page = page;
        }
        
        @Override
        public void openPlaywrightClass() {
            page.navigate("https://playwright.dev/docs/api/class-playwright");
        }
        
        @Override
        public void openPageClass() {
            page.navigate("https://playwright.dev/docs/api/class-page");
        }
        
        @Override
        public void openLocatorClass() {
            page.navigate("https://playwright.dev/docs/api/class-locator");
        }
        
        @Override
        public void openApiClass(String className) {
            page.navigate("https://playwright.dev/docs/api/" + className);
        }
        
        @Override
        public void verifyPageLoaded() {
            assertThat(page.locator("article")).isVisible();
        }
        
        @Override
        public void verifyHeading(String text) {
            assertThat(page.locator("h1")).containsText(text);
        }
        
        @Override
        public void verifySidebar() {
            assertThat(page.locator(".theme-doc-sidebar-menu")).isVisible();
        }
        
        @Override
        public void clickBrowserClass() {
            page.locator("a[href='/docs/api/class-browser']").click();
        }
        
        @Override
        public void verifyBrowserClassPage() {
            assertThat(page).hasURL(java.util.regex.Pattern.compile(".*class-browser.*"));
        }
    }
}
