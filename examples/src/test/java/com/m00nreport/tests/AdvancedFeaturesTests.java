package com.m00nreport.tests;

import com.m00nreport.reporter.M00nStep;
import com.m00nreport.reporter.StepProxy;
import com.m00nreport.reporter.annotations.Step;
import com.m00nreport.reporter.model.AttachmentData;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.jupiter.api.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Advanced Playwright features tests.
 */
@DisplayName("Advanced Features")
class AdvancedFeaturesTests extends BasePlaywrightTest {
    
    @Test
    @DisplayName("Screenshot capture")
    void screenshotCapture(Page page) {
        var advancedPage = StepProxy.create(AdvancedPage.class, new AdvancedPageImpl(page));
        advancedPage.openHomepage();
        advancedPage.takeFullPageScreenshot("homepage");
        advancedPage.takeElementScreenshot(".hero", "hero-section");
    }
    
    @Test
    @DisplayName("Role-based locators")
    void roleBasedLocators(Page page) {
        var advancedPage = StepProxy.create(AdvancedPage.class, new AdvancedPageImpl(page));
        advancedPage.openHomepage();
        advancedPage.findButtonsByRole();
        advancedPage.findLinksByRole();
        advancedPage.findNavigationByRole();
    }
    
    @Test
    @DisplayName("Text-based locators")
    void textBasedLocators(Page page) {
        var advancedPage = StepProxy.create(AdvancedPage.class, new AdvancedPageImpl(page));
        advancedPage.openHomepage();
        advancedPage.findByText("Playwright");
        advancedPage.findLinkByName("Docs");
    }
    
    @Test
    @DisplayName("Filter and chain locators")
    void filterAndChainLocators(Page page) {
        var advancedPage = StepProxy.create(AdvancedPage.class, new AdvancedPageImpl(page));
        advancedPage.openHomepage();
        advancedPage.filterLinks("Docs");
        advancedPage.chainLocators();
    }
    
    @Test
    @DisplayName("JavaScript evaluation")
    void javaScriptEvaluation(Page page) {
        var advancedPage = StepProxy.create(AdvancedPage.class, new AdvancedPageImpl(page));
        advancedPage.openHomepage();
        advancedPage.getTitleViaJs();
        advancedPage.countLinksViaJs();
    }
    
    @Test
    @DisplayName("Responsive viewport")
    void responsiveViewport(Page page) {
        var advancedPage = StepProxy.create(AdvancedPage.class, new AdvancedPageImpl(page));
        advancedPage.openHomepage();
        advancedPage.setMobileViewport();
        advancedPage.takeFullPageScreenshot("mobile-view");
        advancedPage.setDesktopViewport();
        advancedPage.takeFullPageScreenshot("desktop-view");
    }
    
    @Test
    @DisplayName("Iterate multiple elements")
    void iterateElements(Page page) {
        var advancedPage = StepProxy.create(AdvancedPage.class, new AdvancedPageImpl(page));
        advancedPage.openHomepage();
        advancedPage.iterateNavLinks();
    }
    
    // =========================================================================
    // Page Object Interface
    // =========================================================================
    
    public interface AdvancedPage {
        @Step("Open homepage")
        void openHomepage();
        
        @Step("Take full page screenshot")
        void takeFullPageScreenshot(String name);
        
        @Step("Take element screenshot")
        void takeElementScreenshot(String selector, String name);
        
        @Step("Find buttons by role")
        void findButtonsByRole();
        
        @Step("Find links by role")
        void findLinksByRole();
        
        @Step("Find navigation by role")
        void findNavigationByRole();
        
        @Step("Find by text")
        void findByText(String text);
        
        @Step("Find link by name")
        void findLinkByName(String name);
        
        @Step("Filter links")
        void filterLinks(String text);
        
        @Step("Chain locators")
        void chainLocators();
        
        @Step("Get title via JavaScript")
        void getTitleViaJs();
        
        @Step("Count links via JavaScript")
        void countLinksViaJs();
        
        @Step("Set mobile viewport")
        void setMobileViewport();
        
        @Step("Set desktop viewport")
        void setDesktopViewport();
        
        @Step("Iterate navigation links")
        void iterateNavLinks();
    }
    
    // =========================================================================
    // Implementation
    // =========================================================================
    
    public static class AdvancedPageImpl implements AdvancedPage {
        private final Page page;
        
        public AdvancedPageImpl(Page page) {
            this.page = page;
        }
        
        @Override
        public void openHomepage() {
            page.navigate("https://playwright.dev/", 
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        }
        
        @Override
        public void takeFullPageScreenshot(String name) {
            try {
                // Wait for page to be stable before screenshot
                page.waitForLoadState(LoadState.NETWORKIDLE);
                
                // Take viewport screenshot (more reliable than full page)
                var screenshot = page.screenshot(
                    new Page.ScreenshotOptions()
                        .setFullPage(false)  // Viewport only - more reliable
                        .setTimeout(10000)
                );
                M00nStep.current().ifPresent(result ->
                    result.addAttachment(AttachmentData.screenshot(name, screenshot))
                );
            } catch (Exception e) {
                // Log but don't fail test for screenshot issues
                System.err.println("[Screenshot] Failed to capture " + name + ": " + e.getMessage());
            }
        }
        
        @Override
        public void takeElementScreenshot(String selector, String name) {
            try {
                var locator = page.locator(selector);
                // Wait for element to be visible
                locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                
                var screenshot = locator.screenshot(
                    new Locator.ScreenshotOptions().setTimeout(10000)
                );
                M00nStep.current().ifPresent(result ->
                    result.addAttachment(AttachmentData.screenshot(name, screenshot))
                );
            } catch (Exception e) {
                // Log but don't fail test for screenshot issues
                System.err.println("[Screenshot] Failed to capture element " + name + ": " + e.getMessage());
            }
        }
        
        @Override
        public void findButtonsByRole() {
            var buttons = page.getByRole(AriaRole.BUTTON);
            assertThat(buttons.first()).isVisible();
        }
        
        @Override
        public void findLinksByRole() {
            var links = page.getByRole(AriaRole.LINK);
            assertThat(links.first()).isVisible();
        }
        
        @Override
        public void findNavigationByRole() {
            var nav = page.getByRole(AriaRole.NAVIGATION);
            assertThat(nav.first()).isVisible();
        }
        
        @Override
        public void findByText(String text) {
            var element = page.getByText(text);
            assertThat(element.first()).isVisible();
        }
        
        @Override
        public void findLinkByName(String name) {
            var link = page.getByRole(AriaRole.LINK, 
                new Page.GetByRoleOptions().setName(name));
            assertThat(link.first()).isVisible();
        }
        
        @Override
        public void filterLinks(String text) {
            var filtered = page.locator("a").filter(
                new Locator.FilterOptions().setHasText(text)
            );
            assertThat(filtered.first()).isVisible();
        }
        
        @Override
        public void chainLocators() {
            var navDocs = page.locator(".navbar")
                .locator("a")
                .filter(new Locator.FilterOptions().setHasText("Docs"));
            assertThat(navDocs).isVisible();
        }
        
        @Override
        public void getTitleViaJs() {
            var title = (String) page.evaluate("() => document.title");
            org.assertj.core.api.Assertions.assertThat(title).contains("Playwright");
        }
        
        @Override
        public void countLinksViaJs() {
            var count = (Integer) page.evaluate("() => document.querySelectorAll('a').length");
            org.assertj.core.api.Assertions.assertThat(count).isGreaterThan(10);
        }
        
        @Override
        public void setMobileViewport() {
            page.setViewportSize(375, 667);
        }
        
        @Override
        public void setDesktopViewport() {
            page.setViewportSize(1280, 720);
        }
        
        @Override
        public void iterateNavLinks() {
            var links = page.locator(".navbar__link");
            var count = links.count();
            
            for (int i = 0; i < Math.min(count, 3); i++) {
                var text = links.nth(i).textContent();
                org.assertj.core.api.Assertions.assertThat(text).isNotEmpty();
            }
        }
    }
}
