package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 015 AC21 (chỉ báo) – `desktop_indicator` trên thanh trên. */
@RunWith(RobolectricTestRunner::class)
class BrowserTopBarDesktopTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val desktop =
        BrowserUiState(
            url = "https://a.com/",
            displayHost = "a.com",
            canToggleDesktopSite = true,
            isDesktopSite = true,
        )

    private fun show(state: BrowserUiState) {
        rule.setContent { BrowserTopBar(state = state, actions = BrowserActions()) }
    }

    @Test
    fun ac21_indicator_shown_whenDesktop_andNotEditing_withDescription() {
        show(desktop)
        rule.onNodeWithTag(BrowserTestTags.DESKTOP_INDICATOR).assertIsDisplayed()
        assertTrue(
            rule.onNodeWithTag(BrowserTestTags.DESKTOP_INDICATOR).contentDescriptions().any { it.isNotBlank() },
        )
    }

    @Test
    fun ac21_indicator_hidden_whenMobile() {
        show(desktop.copy(isDesktopSite = false))
        rule.onNodeWithTag(BrowserTestTags.DESKTOP_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun ac21_indicator_hidden_whileEditingUrl() {
        show(desktop.copy(isEditingUrl = true))
        rule.onNodeWithTag(BrowserTestTags.DESKTOP_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun ac21_indicator_hidden_atHome() {
        show(BrowserUiState(isHome = true, isDesktopSite = true))
        rule.onNodeWithTag(BrowserTestTags.DESKTOP_INDICATOR).assertDoesNotExist()
    }
}
