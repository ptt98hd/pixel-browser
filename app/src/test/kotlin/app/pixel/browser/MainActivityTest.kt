package app.pixel.browser

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.UrlDisplay
import app.pixel.browser.core.omnibox.homeUrl
import app.pixel.browser.feature.browser.BrowserTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(
        tag: String,
        unmerged: Boolean = false,
    ): String =
        rule
            .onNodeWithTag(tag, useUnmergedTree = unmerged)
            .fetchSemanticsNode()
            .config
            .getOrElse(SemanticsProperties.Text) { emptyList() }
            .joinToString(" ") { it.text }

    @Test
    fun launches() {
        assertNotNull(rule.activity)
    }

    /** Spec 005 AC1: lần đầu mở app → 1 tab được chọn tải homeUrl, engine hiển thị. */
    @Test
    fun ac1_firstLaunch_showsSingleHomeTab_withEngine() {
        rule.waitForIdle()
        rule.onNodeWithTag(BrowserTestTags.ENGINE_CONTAINER, useUnmergedTree = true).assertExists()
        assertEquals("1", text(BrowserTestTags.TAB_COUNT, unmerged = true))
        assertEquals(UrlDisplay.host(SearchEngines.Default.homeUrl), text(BrowserTestTags.URL_DISPLAY))
    }
}
