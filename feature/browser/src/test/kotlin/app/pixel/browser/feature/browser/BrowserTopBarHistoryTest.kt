package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.core.omnibox.Suggestion
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.feature.browser.BrowserTestTags.suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 012 AC13 – `BrowserTopBar` hiển thị hai kind mới. */
@RunWith(RobolectricTestRunner::class)
class BrowserTopBarHistoryTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        suggestions: List<Suggestion>,
        actions: BrowserActions = BrowserActions(),
    ) {
        rule.setContent {
            BrowserTopBar(
                state = BrowserUiState(url = "https://x.com", isEditingUrl = true, suggestions = suggestions),
                actions = actions,
            )
        }
    }

    @Test
    fun ac13_historyRow_showsTitle() {
        show(listOf(Suggestion(SuggestionKind.History, "Kotlin - Wikipedia", "https://en.wikipedia.org/wiki/Kotlin")))
        assertEquals("Kotlin - Wikipedia", rule.onNodeWithTag(suggestion(0)).textValue())
    }

    @Test
    fun ac13_historyRow_withEmptyTitle_showsUrl() {
        show(listOf(Suggestion(SuggestionKind.History, "", "https://wikia.com/")))
        assertEquals("https://wikia.com/", rule.onNodeWithTag(suggestion(0)).textValue())
    }

    @Test
    fun ac13_bookmarkRow_showsTitle() {
        show(listOf(Suggestion(SuggestionKind.Bookmark, "My bookmark", "https://b.com/")))
        assertEquals("My bookmark", rule.onNodeWithTag(suggestion(0)).textValue())
    }

    @Test
    fun ac13_tapHistoryRow_callsOnSuggestionClick() {
        val s = Suggestion(SuggestionKind.History, "Kotlin", "https://k.org/")
        val clicked = mutableListOf<Suggestion>()
        show(listOf(s), BrowserActions(onSuggestionClick = { clicked += it }))
        rule.onNodeWithTag(suggestion(0)).performClick()
        assertTrue(clicked == listOf(s))
    }
}
