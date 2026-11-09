package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 013 AC19 – `BrowserTopBar.bookmark_indicator`: sao đặc cạnh domain, không nhận chạm. */
@RunWith(RobolectricTestRunner::class)
class BrowserTopBarBookmarkTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: BrowserUiState) {
        rule.setContent { BrowserTopBar(state = state, actions = BrowserActions()) }
    }

    private val saved =
        BrowserUiState(
            url = "https://a.com/",
            displayHost = "a.com",
            isBookmarked = true,
            bookmarkId = 3,
            canBookmark = true,
        )

    @Test
    fun indicator_isShown_whenBookmarked_andIsNotClickable() {
        show(saved)
        rule.onNodeWithTag(BrowserTestTags.BOOKMARK_INDICATOR).assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun indicator_isHidden_whenNotBookmarked() {
        show(saved.copy(isBookmarked = false, bookmarkId = null))
        rule.onNodeWithTag(BrowserTestTags.BOOKMARK_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun indicator_isHidden_whileEditingUrl() {
        show(saved.copy(isEditingUrl = true))
        rule.onNodeWithTag(BrowserTestTags.BOOKMARK_INDICATOR).assertDoesNotExist()
    }
}
