package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.pixel.browser.feature.browser.BrowserTestTags.PRIVATE_INDICATOR
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 016 AC18 – chỉ báo ẩn danh trên thanh trên. */
@RunWith(RobolectricTestRunner::class)
class BrowserTopBarPrivateTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: BrowserUiState) {
        rule.setContent { BrowserTopBar(state = state, actions = BrowserActions()) }
        rule.waitForIdle()
    }

    private val page = BrowserUiState(url = "https://a.com/", displayHost = "a.com")

    @Test
    fun ac18_indicator_isShown_withContentDescription_whenPrivateSelected() {
        show(page.copy(selectedIsPrivate = true))
        rule.onNodeWithTag(PRIVATE_INDICATOR).assertExists()
        assertTrue(rule.onNodeWithTag(PRIVATE_INDICATOR).contentDescriptions().any { it.isNotBlank() })
    }

    @Test
    fun ac18_indicator_isShown_onPrivateHome() {
        show(BrowserUiState(isHome = true, selectedIsPrivate = true))
        rule.onNodeWithTag(PRIVATE_INDICATOR).assertExists()
    }

    @Test
    fun ac18_indicator_isAbsent_forNormalTab() {
        show(page)
        rule.onNodeWithTag(PRIVATE_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun ac18_indicator_isAbsent_forNormalHome() {
        show(BrowserUiState(isHome = true))
        rule.onNodeWithTag(PRIVATE_INDICATOR).assertDoesNotExist()
    }
}
