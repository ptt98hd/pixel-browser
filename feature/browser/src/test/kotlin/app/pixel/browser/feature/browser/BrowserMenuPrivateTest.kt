package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_NEW_PRIVATE_TAB
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 016 AC17 – mục menu "Tab ẩn danh". */
@RunWith(RobolectricTestRunner::class)
class BrowserMenuPrivateTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var opened = 0
    private val actions = BrowserActions(onNewPrivateTab = { opened++ })
    private val page = BrowserUiState(url = "https://a.com/", tabCount = 1, canOpenPrivate = true)

    private fun showMenu(state: BrowserUiState) {
        rule.setContent { BrowserScreen(state = state, actions = actions) }
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.waitForIdle()
    }

    @Test
    fun ac17_item_isShown_andEnabled_whenAvailable() {
        showMenu(page)
        rule.onNodeWithTag(MENU_NEW_PRIVATE_TAB).assertExists().assertIsEnabled()
    }

    @Test
    fun ac17_item_isShownButDisabled_whenNotAvailable() {
        showMenu(page.copy(canOpenPrivate = false))
        rule.onNodeWithTag(MENU_NEW_PRIVATE_TAB).assertExists().assertIsNotEnabled()
    }

    @Test
    fun ac17_tap_callsNewPrivateTabOnce_andClosesMenu() {
        showMenu(page)
        rule.onNodeWithTag(MENU_NEW_PRIVATE_TAB).performClick()
        rule.waitForIdle()
        assertEquals(1, opened)
        rule.onNodeWithTag(MENU_NEW_PRIVATE_TAB).assertDoesNotExist()
    }

    @Test
    fun ac17_item_isAlsoShown_onHomeAndOnPrivateTab() {
        showMenu(page.copy(isHome = true, selectedIsPrivate = true))
        rule.onNodeWithTag(MENU_NEW_PRIVATE_TAB).assertExists()
    }

    @Test
    fun ac17_unavailableMessage_isShownAsExplanation_notAsRawCode() {
        rule.setContent {
            BrowserScreen(
                state = page.copy(canOpenPrivate = false, message = BrowserMessages.PRIVATE_UNAVAILABLE),
                actions = actions,
            )
        }
        rule.waitForIdle()
        rule.onAllNodesWithText(BrowserMessages.PRIVATE_UNAVAILABLE).assertCountEquals(0)
    }
}
