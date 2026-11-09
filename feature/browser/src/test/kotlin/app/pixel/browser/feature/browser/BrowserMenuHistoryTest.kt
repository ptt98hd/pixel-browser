package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_HISTORY
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 012 AC19 (phần `:feature:browser`) – menu có "Lịch sử", luôn bật kể cả ở home. */
@RunWith(RobolectricTestRunner::class)
class BrowserMenuHistoryTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var opened = 0

    private fun show(state: BrowserUiState) {
        rule.setContent { BrowserScreen(state = state, actions = BrowserActions(onOpenHistory = { opened++ })) }
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.waitForIdle()
    }

    @Test
    fun ac19_menuHistory_existsAndEnabled_onWebPage() {
        show(BrowserUiState(url = "https://a.com/", tabCount = 1))
        rule.onNodeWithTag(MENU_HISTORY).assertExists().assertIsEnabled()
    }

    @Test
    fun ac19_menuHistory_enabled_atHome() {
        show(BrowserUiState(isHome = true, tabCount = 1))
        rule.onNodeWithTag(MENU_HISTORY).assertExists().assertIsEnabled()
    }

    @Test
    fun ac19_tapMenuHistory_callsOnOpenHistoryOnce_andClosesMenu() {
        show(BrowserUiState(url = "https://a.com/", tabCount = 1))
        rule.onNodeWithTag(MENU_HISTORY).performClick()
        rule.waitForIdle()
        assertEquals(1, opened)
        rule.onNodeWithTag(MENU_HISTORY).assertDoesNotExist()
    }
}
