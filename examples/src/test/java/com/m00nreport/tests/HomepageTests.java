package com.m00nreport.tests;

import com.m00nreport.reporter.annotations.Step;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Homepage tests using AspectJ-based step tracking.
 * 
 * No StepProxy or PageFactory needed - AspectJ intercepts @Step methods automatically!
 */
@DisplayName("Playwright Homepage Tests")
class HomepageTests extends BasePlaywrightTest {
    
    @Test
    @DisplayName("Homepage loads successfully")
    void homepageLoads(Page page) {
        var homePage = new HomePage(page);
        homePage.open();
        homePage.verifyLoaded();
    }
    
    @Test
    @DisplayName("Hero section is visible")
    void heroSectionVisible(Page page) {
        var homePage = new HomePage(page);
        homePage.open();
        homePage.verifyHeroTitle();
        homePage.verifyHeroSubtitle();
    }
    
    @Test
    @DisplayName("Navigation bar is present")
    void navigationPresent(Page page) {
        var homePage = new HomePage(page);
        homePage.open();
        homePage.verifyNavigation();
    }
    
    @Test
    @DisplayName("Get Started button exists")
    void getStartedButton(Page page) {
        var homePage = new HomePage(page);
        homePage.open();
        homePage.verifyGetStartedButton();
    }
    
    @Test
    @DisplayName("Search is available")
    void searchAvailable(Page page) {
        var homePage = new HomePage(page);
        homePage.open();
        homePage.verifySearchButton();
    }
    
    // =========================================================================
    // Page Object Class with @Step annotations
    // =========================================================================
    
    private static class HomePage {
        private final Page page;
        
        HomePage(Page page) {
            this.page = page;
        }
        
        @Step("Open Playwright homepage")
        public void open() {
            page.navigate("https://playwright.dev/");
        }
        
        @Step("Verify homepage is loaded")
        public void verifyLoaded() {
            assertThat(page).hasTitle(java.util.regex.Pattern.compile(".*Playwright.*"));
        }
        
        @Step("Verify hero title")
        public void verifyHeroTitle() {
            assertThat(page.locator(".hero__title")).isVisible();
            assertThat(page.locator(".hero__title")).containsText("Playwright");
        }
        
        @Step("Verify hero subtitle")
        public void verifyHeroSubtitle() {
            assertThat(page.locator(".hero__subtitle")).isVisible();
        }
        
        @Step("Verify navigation bar")
        public void verifyNavigation() {
            assertThat(page.locator(".navbar")).isVisible();
            assertThat(page.locator(".navbar__items")).isVisible();
        }
        
        @Step("Verify Get Started button")
        public void verifyGetStartedButton() {
            var button = page.locator("a.button--primary");
            assertThat(button).isVisible();
            assertThat(button).isEnabled();
        }
        
        @Step("Verify search button")
        public void verifySearchButton() {
            assertThat(page.locator("button.DocSearch")).isVisible();
        }
    }
}
