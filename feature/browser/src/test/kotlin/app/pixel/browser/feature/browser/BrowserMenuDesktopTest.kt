package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_DESKTOP_SITE
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 015 AC21 (menu) – mục "Trang web máy tính" có dấu tích. */
@RunWith(RobolectricTestRunner::class)
class BrowserMenuDesktopTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var toggled = 0
    private val actions = BrowserActions(onToggleDesktopSite = { toggled++ })
    private val page =
        BrowserUiState(url = "https://a.com/", tabCount = 1, canToggleDesktopSite = true, isDesktopSite = false)

    private fun showMenu(state: BrowserUiState) {
        rule.setContent { BrowserScreen(state = state, actions = actions) }
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.waitForIdle()
    }

    @Test
    fun ac21_checkbox_isOff_whenMobile() {
        showMenu(page)
        rule.onNodeWithTag(MENU_DESKTOP_SITE).assertExists().assertIsOff()
    }

    @Test
    fun ac21_checkbox_isOn_whenDesktop() {
        showMenu(page.copy(isDesktopSite = true))
        rule.onNodeWithTag(MENU_DESKTOP_SITE).assertIsOn()
        assertEquals(
            ToggleableState.On,
            rule.onNodeWithTag(MENU_DESKTOP_SITE).fetchSemanticsNode().config[SemanticsProperties.ToggleableState],
        )
    }

    @Test
    fun ac21_checkbox_followsStateChanges() {
        var state by mutableStateOf(page)
        rule.setContent { BrowserScreen(state = state, actions = actions) }
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_DESKTOP_SITE).assertIsOff()
        state = page.copy(isDesktopSite = true)
        rule.waitForIdle()
        rule.onNodeWithTag(MENU_DESKTOP_SITE).assertIsOn()
    }

    @Test
    fun ac21_disabled_whenCannotToggle() {
        showMenu(page.copy(canToggleDesktopSite = false))
        rule.onNodeWithTag(MENU_DESKTOP_SITE).assertExists().assertIsNotEnabled()
    }

    @Test
    fun ac21_enabled_whenCanToggle() {
        showMenu(page)
        rule.onNodeWithTag(MENU_DESKTOP_SITE).assertIsEnabled()
    }

    @Test
    fun ac21_tap_callsToggleOnce_andClosesMenu() {
        showMenu(page)
        rule.onNodeWithTag(MENU_DESKTOP_SITE).performClick()
        rule.waitForIdle()
        assertEquals(1, toggled)
        rule.onNodeWithTag(MENU_DESKTOP_SITE).assertDoesNotExist()
    }
}
