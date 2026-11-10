package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.PRIVATE_LANDING
import app.pixel.browser.feature.browser.BrowserTestTags.PRIVATE_LANDING_SEARCH
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 016 AC18 – trang chủ của tab ẩn danh là `PrivateLanding`, không phải trang chủ có shortcut. */
@RunWith(RobolectricTestRunner::class)
class PrivateLandingTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var startedEditing = 0
    private val actions = BrowserActions(onStartEditing = { startedEditing++ })

    private fun show(state: BrowserUiState) {
        rule.setContent {
            BrowserScreen(
                state = state,
                actions = actions,
                homeContent = { Box(Modifier.fillMaxSize().testTag("home_shortcuts")) },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun ac18_privateHome_showsLanding_insteadOfUserHome() {
        show(BrowserUiState(isHome = true, selectedIsPrivate = true, tabCount = 1))
        rule.onNodeWithTag(PRIVATE_LANDING).assertExists()
        rule.onNodeWithTag("home_shortcuts").assertDoesNotExist()
        rule.onNodeWithTag(PRIVATE_LANDING_SEARCH).assertExists()
    }

    @Test
    fun ac18_landingSearch_tap_startsEditing() {
        show(BrowserUiState(isHome = true, selectedIsPrivate = true, tabCount = 1))
        rule.onNodeWithTag(PRIVATE_LANDING_SEARCH).performClick()
        rule.waitForIdle()
        assertEquals(1, startedEditing)
    }

    @Test
    fun ac18_normalHome_showsUserHome_noLanding() {
        show(BrowserUiState(isHome = true, tabCount = 1))
        rule.onNodeWithTag("home_shortcuts").assertExists()
        rule.onNodeWithTag(PRIVATE_LANDING).assertDoesNotExist()
    }

    @Test
    fun ac18_privateTabWithPage_showsNoLanding() {
        show(BrowserUiState(url = "https://a.com/", selectedIsPrivate = true, tabCount = 1))
        rule.onNodeWithTag(PRIVATE_LANDING).assertDoesNotExist()
    }
}
