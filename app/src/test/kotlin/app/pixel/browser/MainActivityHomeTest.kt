package app.pixel.browser

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.BrowserViewModel
import app.pixel.browser.feature.home.HomeTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 010 – AC19 (phần `:app`): `HomeRoute` được nối vào slot home của `BrowserRoute`. */
@RunWith(RobolectricTestRunner::class)
class MainActivityHomeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun vm(): BrowserViewModel = ViewModelProvider(rule.activity)[BrowserViewModel::class.java]

    @Test
    fun ac19_tapHomeSearch_focusesOmnibox() {
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.HOME_PAGE).assertExists()
        rule.onNodeWithTag(HomeTestTags.HOME_SEARCH).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BrowserTestTags.URL_FIELD).assertExists().assertIsFocused()
        assertTrue(vm().uiState.value.isEditingUrl)
    }

    /** Tag `home_shortcut_<id>` (không tính nút thêm). */
    private val shortcutTile =
        SemanticsMatcher("is a home shortcut") {
            val tag = it.config.getOrElse(SemanticsProperties.TestTag) { "" }
            tag.startsWith("home_shortcut_") && tag != HomeTestTags.SHORTCUT_ADD
        }

    @Test
    fun ac19_homeShowsShortcuts_andTapOpensItInSelectedTab() {
        rule.waitForIdle()
        val tiles = rule.onAllNodes(shortcutTile)
        assertTrue("shortcuts must be shown", tiles.fetchSemanticsNodes().isNotEmpty())
        tiles[0].performClick()
        rule.waitForIdle()
        assertFalse(vm().uiState.value.isHome)
        assertEquals(1, vm().uiState.value.tabCount)
        rule.onNodeWithTag(HomeTestTags.HOME_PAGE).assertDoesNotExist()
    }
}
