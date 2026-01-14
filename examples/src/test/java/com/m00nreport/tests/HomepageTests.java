package com.m00nreport.tests;

import com.m00nreport.reporter.StepProxy;
import com.m00nreport.reporter.annotations.Step;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Homepage tests using annotation-based step tracking.
 * 
 * No @M00nTest annotation needed - reporter is auto-registered!
 */
@DisplayName("Playwright Homepage Tests")
class HomepageTests extends BasePlaywrightTest {
    
    @Test
    @DisplayName("Homepage loads successfully")
    void homepageLoads(Page page) {
        var homePage = StepProxy.create(HomePage.class, new HomePageImpl(page));
        homePage.open();
        homePage.verifyLoaded();
    }
    
    @Test
    @DisplayName("Hero section is visible")
    void heroSectionVisible(Page page) {
        var homePage = StepProxy.create(HomePage.class, new HomePageImpl(page));
        homePage.open();
        homePage.verifyHeroTitle();
        homePage.verifyHeroSubtitle();
    }
    
    @Test
    @DisplayName("Navigation bar is present")
    void navigationPresent(Page page) {
        var homePage = StepProxy.create(HomePage.class, new HomePageImpl(page));
        homePage.open();
        homePage.verifyNavigation();
    }
    
    @Test
    @DisplayName("Get Started button exists")
    void getStartedButton(Page page) {
        var homePage = StepProxy.create(HomePage.class, new HomePageImpl(page));
        homePage.open();
        homePage.verifyGetStartedButton();
    }
    
    @Test
    @DisplayName("Search is available")
    void searchAvailable(Page page) {
        var homePage = StepProxy.create(HomePage.class, new HomePageImpl(page));
        homePage.open();
        homePage.verifySearchButton();
    }
    
    // =========================================================================
    // Page Object Interface
    // =========================================================================
    
    interface HomePage {
        @Step("Open Playwright homepage")
        void open();
        
        @Step("Verify homepage is loaded")
        void verifyLoaded();
        
        @Step("Verify hero title")
        void verifyHeroTitle();
        
        @Step("Verify hero subtitle")
        void verifyHeroSubtitle();
        
        @Step("Verify navigation bar")
        void verifyNavigation();
        
        @Step("Verify Get Started button")
        void verifyGetStartedButton();
        
        @Step("Verify search button")
        void verifySearchButton();
    }
    
    // =========================================================================
    // Implementation using modern Playwright assertions
    // =========================================================================
    
    private static class HomePageImpl implements HomePage {
        private final Page page;
        
        HomePageImpl(Page page) {
            this.page = page;
        }
        
        @Override
        public void open() {
            page.navigate("https://playwright.dev/");
        }
        
        @Override
        public void verifyLoaded() {
            assertThat(page).hasTitle(java.util.regex.Pattern.compile(".*Playwright.*"));
        }
        
        @Override
        public void verifyHeroTitle() {
            assertThat(page.locator(".hero__title")).isVisible();
            assertThat(page.locator(".hero__title")).containsText("Playwright");
        }
        
        @Override
        public void verifyHeroSubtitle() {
            assertThat(page.locator(".hero__subtitle")).isVisible();
        }
        
        @Override
        public void verifyNavigation() {
            assertThat(page.locator(".navbar")).isVisible();
            assertThat(page.locator(".navbar__items")).isVisible();
        }
        
        @Override
        public void verifyGetStartedButton() {
            var button = page.locator("a.button--primary");
            assertThat(button).isVisible();
            assertThat(button).isEnabled();
        }
        
        @Override
        public void verifySearchButton() {
            assertThat(page.locator("button.DocSearch")).isVisible();
        }
    }
}
