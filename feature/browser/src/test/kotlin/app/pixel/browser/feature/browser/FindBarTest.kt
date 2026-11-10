package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.pixel.browser.feature.browser.BrowserTestTags.FIND_BAR
import app.pixel.browser.feature.browser.BrowserTestTags.FIND_CLOSE
import app.pixel.browser.feature.browser.BrowserTestTags.FIND_COUNT
import app.pixel.browser.feature.browser.BrowserTestTags.FIND_FIELD
import app.pixel.browser.feature.browser.BrowserTestTags.FIND_NEXT
import app.pixel.browser.feature.browser.BrowserTestTags.FIND_PREV
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 015 AC8 (nội dung, trợ năng, callback) và AC9 (focus, IME) – `FindBar` (Robolectric + Compose). */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
class FindBarTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private class RecordingKeyboard : SoftwareKeyboardController {
        var shown = 0
        var hidden = 0

        override fun show() {
            shown++
        }

        override fun hide() {
            hidden++
        }
    }

    private val keyboard = RecordingKeyboard()
    private val nexts = mutableListOf<Boolean>()
    private var closed = 0
    private val queries = mutableListOf<String>()

    private fun show(initial: FindUiState) {
        rule.setContent {
            var state by remember { mutableStateOf(initial) }
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                FindBar(
                    state = state,
                    onQueryChange = {
                        queries += it
                        state = state.copy(query = it)
                    },
                    onFindNext = { nexts += it },
                    onClose = { closed++ },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun editable(tag: String): String =
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrElse(SemanticsProperties.EditableText) {
                androidx.compose.ui.text
                    .AnnotatedString("")
            }.text

    // --- AC8 ---

    @Test
    fun ac8_showsFieldCountAndButtons() {
        show(FindUiState("a", 2, 7, false))
        rule.onNodeWithTag(FIND_BAR).assertExists()
        assertEquals("a", editable(FIND_FIELD))
        rule.onNodeWithTag(FIND_COUNT).assertTextIs("2/7")
        rule.onNodeWithTag(FIND_PREV).assertExists().assertIsEnabled()
        rule.onNodeWithTag(FIND_NEXT).assertExists().assertIsEnabled()
        rule.onNodeWithTag(FIND_CLOSE).assertExists().assertIsEnabled()
    }

    @Test
    fun ac8_noMatches_showsZeroOverZero_withNotFoundDescription_andDisablesNavigation() {
        show(FindUiState("zzz", 0, 0, false))
        rule.onNodeWithTag(FIND_COUNT).assertTextIs("0/0")
        assertTrue(rule.onNodeWithTag(FIND_COUNT).contentDescriptions().any { it.isNotBlank() })
        rule.onNodeWithTag(FIND_PREV).assertIsNotEnabled()
        rule.onNodeWithTag(FIND_NEXT).assertIsNotEnabled()
        rule.onNodeWithTag(FIND_CLOSE).assertIsEnabled()
    }

    @Test
    fun ac8_emptyQuery_hidesCount() {
        show(FindUiState("", 0, 0, false))
        rule.onNodeWithTag(FIND_COUNT).assertDoesNotExist()
    }

    @Test
    fun ac8_searching_showsCurrentOverTotal_withoutNotFoundDescription() {
        var state by mutableStateOf(FindUiState("a", 0, 0, false))
        rule.setContent { FindBar(state = state) }
        rule.waitForIdle()
        val notFound = rule.onNodeWithTag(FIND_COUNT).contentDescriptions()
        state = FindUiState("a", 1, 3, true)
        rule.waitForIdle()
        rule.onNodeWithTag(FIND_COUNT).assertTextIs("1/3")
        assertNotEquals(notFound, rule.onNodeWithTag(FIND_COUNT).contentDescriptions())
    }

    @Test
    fun ac8_count_isPoliteLiveRegion() {
        show(FindUiState("a", 2, 7, false))
        val live =
            rule
                .onNodeWithTag(FIND_COUNT)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.LiveRegion)
        assertEquals(LiveRegionMode.Polite, live)
    }

    @Test
    fun ac8_buttons_haveLabels_andAtLeast48dpTouchTargets() {
        show(FindUiState("a", 2, 7, false))
        listOf(FIND_PREV, FIND_NEXT, FIND_CLOSE).forEach { tag ->
            assertTrue("$tag needs a label", rule.onNodeWithTag(tag).contentDescriptions().any { it.isNotBlank() })
            val b = rule.onNodeWithTag(tag).getBoundsInRoot()
            assertTrue("$tag width ${b.width}", b.width >= 48.dp)
            assertTrue("$tag height ${b.height}", b.height >= 48.dp)
        }
    }

    @Test
    fun ac8_prevAndNextLabels_areDistinct() {
        show(FindUiState("a", 2, 7, false))
        assertNotEquals(
            rule.onNodeWithTag(FIND_PREV).contentDescriptions(),
            rule.onNodeWithTag(FIND_NEXT).contentDescriptions(),
        )
    }

    @Test
    fun ac8_tapButtons_callCallbacks() {
        show(FindUiState("a", 2, 7, false))
        rule.onNodeWithTag(FIND_PREV).performClick()
        rule.onNodeWithTag(FIND_NEXT).performClick()
        rule.onNodeWithTag(FIND_CLOSE).performClick()
        assertEquals(listOf(false, true), nexts)
        assertEquals(1, closed)
    }

    @Test
    fun ac8_typing_callsOnQueryChange_forEachChange() {
        show(FindUiState("", 0, 0, false))
        listOf("k", "o", "t").forEach { rule.onNodeWithTag(FIND_FIELD).performTextInput(it) }
        assertEquals(listOf("k", "ko", "kot"), queries)
    }

    // --- AC9 ---

    @Test
    fun ac9_onAppearing_fieldIsFocused_andKeyboardRequested() {
        show(FindUiState("", 0, 0, false))
        rule.onNodeWithTag(FIND_FIELD).assertIsFocused()
        assertTrue("keyboard.show must be called", keyboard.shown >= 1)
    }

    @Test
    fun ac9_field_usesSearchImeAction() {
        show(FindUiState("a", 0, 0, false))
        val ime =
            rule
                .onNodeWithTag(FIND_FIELD)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.ImeAction)
        assertEquals(ImeAction.Search, ime)
    }

    @Test
    fun ac9_imeSearch_findsNext_hidesKeyboard_keepsBarOpen() {
        show(FindUiState("a", 1, 3, false))
        val hiddenBefore = keyboard.hidden
        rule.onNodeWithTag(FIND_FIELD).performImeAction()
        assertEquals(listOf(true), nexts)
        assertTrue("keyboard.hide must be called", keyboard.hidden > hiddenBefore)
        assertEquals(0, closed)
        rule.onNodeWithTag(FIND_BAR).assertExists()
    }

    @Test
    fun ac9_screen_focusesField_whenFindGoesFromNullToOpen() {
        var state by mutableStateOf(BrowserUiState(url = "https://a.com/", tabCount = 1, canFind = true))
        rule.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                BrowserScreen(state = state, actions = BrowserActions())
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(FIND_BAR).assertDoesNotExist()
        assertFalse(keyboard.shown > 0)
        state = state.copy(find = FindUiState("", 0, 0, false))
        rule.waitForIdle()
        rule.onNodeWithTag(FIND_FIELD).assertIsFocused()
        assertTrue(keyboard.shown >= 1)
    }
}
