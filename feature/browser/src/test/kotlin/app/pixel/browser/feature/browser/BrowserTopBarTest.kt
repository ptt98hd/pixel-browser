package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import app.pixel.browser.core.engine.SecurityState
import app.pixel.browser.core.omnibox.Suggestion
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.core.omnibox.UrlDisplay
import app.pixel.browser.feature.browser.BrowserTestTags.PROGRESS
import app.pixel.browser.feature.browser.BrowserTestTags.QR_BUTTON
import app.pixel.browser.feature.browser.BrowserTestTags.SECURITY_INSECURE
import app.pixel.browser.feature.browser.BrowserTestTags.SECURITY_SECURE
import app.pixel.browser.feature.browser.BrowserTestTags.URL_DISPLAY
import app.pixel.browser.feature.browser.BrowserTestTags.URL_FIELD
import app.pixel.browser.feature.browser.BrowserTestTags.suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 005 – A05: AC5–AC9 (+ phần stateless của AC10, AC12). */
@RunWith(RobolectricTestRunner::class)
class BrowserTopBarTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        state: BrowserUiState,
        actions: BrowserActions = BrowserActions(),
        showQrButton: Boolean = false,
    ) {
        rule.setContent { BrowserTopBar(state = state, actions = actions, showQrButton = showQrButton) }
    }

    private fun page(url: String) = BrowserUiState(url = url, displayHost = UrlDisplay.host(url))

    // --- AC5 ---

    @Test
    fun ac5_showsHostWithoutWww() {
        show(page("https://www.example.com/path?q=1"))
        assertEquals("example.com", rule.onNodeWithTag(URL_DISPLAY).textValue())
    }

    @Test
    fun ac5_aboutBlank_showsNonEmptyPlaceholder() {
        show(page("about:blank"))
        assertTrue(rule.onNodeWithTag(URL_DISPLAY).textValue().isNotBlank())
    }

    @Test
    fun ac5_emptyUrl_showsNonEmptyPlaceholder() {
        show(page(""))
        assertTrue(rule.onNodeWithTag(URL_DISPLAY).textValue().isNotBlank())
    }

    // --- AC6 ---

    @Test
    fun ac6_secure_showsSecureLockOnly() {
        show(page("https://example.com").copy(securityState = SecurityState.Secure))
        rule.onNodeWithTag(SECURITY_SECURE, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(SECURITY_INSECURE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ac6_insecure_showsInsecureOnly() {
        show(page("http://example.com").copy(securityState = SecurityState.Insecure))
        rule.onNodeWithTag(SECURITY_INSECURE, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(SECURITY_SECURE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ac6_unknown_showsNoSecurityIcon() {
        show(page("about:blank").copy(securityState = SecurityState.Unknown))
        rule.onNodeWithTag(SECURITY_SECURE, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(SECURITY_INSECURE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ac6_securityIcons_haveContentDescription() {
        show(page("https://example.com").copy(securityState = SecurityState.Secure))
        assertTrue(
            rule
                .onNodeWithTag(SECURITY_SECURE, useUnmergedTree = true)
                .contentDescriptions()
                .any { it.isNotBlank() },
        )
    }

    // --- AC7 ---

    @Test
    fun ac7_loading_showsProgressFraction() {
        show(page("https://example.com").copy(isLoading = true, progress = 40))
        assertEquals(0.4f, rule.onNodeWithTag(PROGRESS, useUnmergedTree = true).progressFraction(), 0.001f)
    }

    @Test
    fun ac7_notLoading_hidesProgress() {
        show(page("https://example.com").copy(isLoading = false, progress = 100))
        rule.onNodeWithTag(PROGRESS, useUnmergedTree = true).assertDoesNotExist()
    }

    // --- AC8 ---

    @Test
    fun ac8_qrButton_hiddenByDefault() {
        show(page("https://example.com"))
        rule.onNodeWithTag(URL_DISPLAY).assertExists()
        rule.onNodeWithTag(QR_BUTTON, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ac8_qrButton_shownWhenFlagged_andDefaultCallbackIsNoOp() {
        show(page("https://example.com"), showQrButton = true)
        rule.onNodeWithTag(QR_BUTTON).assertExists().performClick()
        rule.onNodeWithTag(URL_DISPLAY).assertExists()
    }

    // --- AC9 ---

    @Test
    fun ac9_tapDisplay_entersEditing_focused_withAllTextSelected() {
        val url = "https://www.example.com/path?q=1"
        var state by mutableStateOf(page(url))
        rule.setContent {
            BrowserTopBar(
                state = state,
                actions = BrowserActions(onStartEditing = { state = state.copy(isEditingUrl = true) }),
            )
        }
        rule.onNodeWithTag(URL_FIELD).assertDoesNotExist()

        rule.onNodeWithTag(URL_DISPLAY).performClick()

        val expected = UrlDisplay.forEditing(url)
        val field = rule.onNodeWithTag(URL_FIELD)
        field.assertIsFocused()
        val config = field.fetchSemanticsNode().config
        assertEquals(expected, config[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(0, expected.length), config[SemanticsProperties.TextSelectionRange])
    }

    // --- AC10 / AC12 (stateless) ---

    @Test
    fun ac10_imeGo_submitsTypedText() {
        val submitted = mutableListOf<String>()
        val queries = mutableListOf<String>()
        show(
            page("https://example.org").copy(isEditingUrl = true),
            BrowserActions(onSubmit = { submitted += it }, onQueryChange = { queries += it }),
        )
        rule.onNodeWithTag(URL_FIELD).performTextReplacement("example.com")
        rule.onNodeWithTag(URL_FIELD).performImeAction()
        assertEquals(listOf("example.com"), submitted)
        assertEquals("example.com", queries.last())
    }

    @Test
    fun ac12_suggestions_renderedWithIndexTags_andClickable() {
        val s =
            listOf(
                Suggestion(SuggestionKind.GoTo, "https://example.com", "https://example.com"),
                Suggestion(SuggestionKind.Search, "Search for example.com", "https://s/?q=example.com"),
            )
        val clicked = mutableListOf<Suggestion>()
        show(
            page("https://example.org").copy(isEditingUrl = true, suggestions = s),
            BrowserActions(onSuggestionClick = { clicked += it }),
        )
        rule.onNodeWithTag(suggestion(0)).assertExists()
        rule.onNodeWithTag(suggestion(1)).performClick()
        assertEquals(listOf(s[1]), clicked)
    }
}
