package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_HISTORY
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_SETTINGS
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_THEME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 014 AC18 (phần `:feature:browser`) – menu có "Cài đặt", luôn bật, nằm cuối menu. */
@RunWith(RobolectricTestRunner::class)
class BrowserMenuSettingsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var opened = 0

    private fun show(state: BrowserUiState) {
        rule.setContent { BrowserScreen(state = state, actions = BrowserActions(onOpenSettings = { opened++ })) }
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.waitForIdle()
    }

    @Test
    fun ac18_menuSettings_existsAndEnabled_onWebPage() {
        show(BrowserUiState(url = "https://a.com/", tabCount = 1))
        rule.onNodeWithTag(MENU_SETTINGS).assertExists().assertIsEnabled()
    }

    @Test
    fun ac18_menuSettings_enabled_atHome() {
        show(BrowserUiState(isHome = true, tabCount = 1))
        rule.onNodeWithTag(MENU_SETTINGS).assertExists().assertIsEnabled()
    }

    @Test
    fun ac18_menuSettings_isLastItem() {
        show(BrowserUiState(url = "https://a.com/", tabCount = 1))
        val bottom = { tag: String ->
            rule
                .onNodeWithTag(tag)
                .fetchSemanticsNode()
                .boundsInRoot.top
        }
        assertTrue(bottom(MENU_SETTINGS) > bottom(MENU_THEME))
        assertTrue(bottom(MENU_SETTINGS) > bottom(MENU_HISTORY))
    }

    @Test
    fun ac18_tapMenuSettings_callsOnOpenSettingsOnce_andClosesMenu() {
        show(BrowserUiState(url = "https://a.com/", tabCount = 1))
        rule.onNodeWithTag(MENU_SETTINGS).performClick()
        rule.waitForIdle()
        assertEquals(1, opened)
        rule.onNodeWithTag(MENU_SETTINGS).assertDoesNotExist()
    }
}
