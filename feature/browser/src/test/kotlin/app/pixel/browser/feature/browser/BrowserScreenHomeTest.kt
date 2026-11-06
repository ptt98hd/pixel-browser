package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_BACK
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_HOME
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.ENGINE_CONTAINER
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_SHARE
import app.pixel.browser.feature.browser.BrowserTestTags.URL_DISPLAY
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 010 – AC18: slot `homeContent` của [BrowserScreen] / [BrowserRoute]. */
@RunWith(RobolectricTestRunner::class)
class BrowserScreenHomeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val page = BrowserUiState(url = "https://a.com/", displayHost = "a.com", tabCount = 1)

    private fun showScreen(state: BrowserUiState) {
        rule.setContent {
            BrowserScreen(
                state = state,
                actions = BrowserActions(),
                engineContent = { Box(Modifier.testTag(ENGINE_CONTAINER)) },
                homeContent = { Box(Modifier.testTag("x")) },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun ac18_isHome_showsHomeContent_notEngine() {
        showScreen(page.copy(isHome = true, url = "", displayHost = ""))
        rule.onNodeWithTag("x").assertExists()
        rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ac18_notHome_showsEngine_notHomeContent() {
        showScreen(page.copy(isHome = false))
        rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("x").assertDoesNotExist()
    }

    // --- BrowserRoute + ViewModel + Fake ---

    private lateinit var f: BrowserFixture

    private fun launchRoute(fixture: BrowserFixture) {
        f = fixture
        rule.setContent { BrowserRoute(viewModel = f.viewModel, homeContent = { Box(Modifier.testTag("x")) }) }
        rule.waitForIdle()
    }

    @Test
    fun ac18_route_initialTab_showsHomeContent_andPlaceholder() {
        launchRoute(BrowserFixture())
        rule.onNodeWithTag("x").assertExists()
        rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(URL_DISPLAY).assertTextIs(rule.activity.getString(R.string.browser_url_placeholder))
        rule.onNodeWithTag(BTN_BACK).assertIsNotEnabled()
    }

    @Test
    fun ac18_route_shareDisabledAtHome() {
        launchRoute(BrowserFixture.onPage("https://a.com/"))
        rule.onNodeWithTag(BTN_HOME).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_SHARE).assertIsNotEnabled()
    }

    @Test
    fun ac18_route_tapHomeOnPage_showsHome_backEnabled_systemBackReturns() {
        launchRoute(BrowserFixture.onPage("https://a.com/"))
        rule.onNodeWithTag("x").assertDoesNotExist()
        rule.onNodeWithTag(BTN_HOME).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("x").assertExists()
        rule.onNodeWithTag(BTN_BACK).assertIsEnabled()
        assertTrue(f.viewModel.uiState.value.isHome)

        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("x").assertDoesNotExist()
        rule.onNodeWithTag(URL_DISPLAY).assertTextIs("a.com")
        assertFalse(f.viewModel.uiState.value.isHome)
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun ac18_route_onOpenUrlAction_isWiredToViewModel() {
        launchRoute(BrowserFixture())
        rule.runOnIdle { f.viewModel.onOpenUrl("https://a.com/") }
        rule.waitForIdle()
        rule.onNodeWithTag("x").assertDoesNotExist()
        rule.onNodeWithTag(URL_DISPLAY).assertTextIs("a.com")
    }
}
