package app.pixel.browser

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.BrowserViewModel
import app.pixel.browser.feature.browser.R
import app.pixel.browser.feature.home.HomeTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    /**
     * Spec 005 AC1 (sửa theo spec 010): lần đầu mở app → 1 tab được chọn ở trang chủ native (`home_page`),
     * không có engine hiển thị và session không tải URL nào.
     */
    @Test
    fun ac1_firstLaunch_showsSingleHomeTab_withHomePage() {
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.HOME_PAGE).assertExists()
        rule.onNodeWithTag(BrowserTestTags.ENGINE_CONTAINER, useUnmergedTree = true).assertDoesNotExist()
        assertEquals("1", text(BrowserTestTags.TAB_COUNT, unmerged = true))
        assertEquals(rule.activity.getString(R.string.browser_url_placeholder), text(BrowserTestTags.URL_DISPLAY))
        val vm = ViewModelProvider(rule.activity)[BrowserViewModel::class.java]
        assertTrue(vm.uiState.value.isHome)
        assertEquals(
            "",
            vm.selectedSession.value
                ?.state
                ?.value
                ?.url
                .orEmpty(),
        )
    }
}
