package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.ui.PixelSemantics
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_BACK
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_FORWARD
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_HOME
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 005 – A06/A07 stateless: AC14, AC15, AC17, AC18–AC23 (mức composable). */
@RunWith(RobolectricTestRunner::class)
class BrowserBottomBarTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Ghi lại tên callback đã gọi theo thứ tự. */
    private val calls = mutableListOf<String>()

    private val actions =
        BrowserActions(
            onBack = { calls += "back" },
            onForward = { calls += "forward" },
            onHome = { calls += "home" },
            onScrollToTop = { calls += "top" },
            onScrollToBottom = { calls += "bottom" },
            onStartEditing = { calls += "edit" },
            onNewTab = { calls += "newTab" },
            onReload = { calls += "reload" },
        )

    private fun show(state: BrowserUiState = BrowserUiState(url = "https://example.com", tabCount = 1)) {
        rule.setContent {
            BrowserBottomBar(
                state = state,
                actions = actions,
                onTabsClick = { calls += "tabs" },
                onMenuClick = { calls += "menu" },
            )
        }
    }

    private val buttons = listOf(BTN_BACK, BTN_FORWARD, BTN_HOME, BTN_TABS, BTN_MENU)

    // --- AC14 ---

    @Test
    fun ac14_fiveButtons_inOrderLeftToRight() {
        show()
        val lefts = buttons.map { rule.onNodeWithTag(it).getBoundsInRoot().left }
        assertEquals(lefts.sorted(), lefts)
        assertEquals(buttons.size, lefts.distinct().size)
    }

    @Test
    fun ac14_eachButton_hasContentDescription_andTouchTargetAtLeast48dp() {
        show()
        buttons.forEach { tag ->
            val node = rule.onNodeWithTag(tag)
            assertTrue("$tag contentDescription", node.contentDescriptions().any { it.isNotBlank() })
            node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    // --- AC15 ---

    @Test
    fun ac15_cannotGoBackOrForward_disablesThoseButtons_only() {
        show(BrowserUiState(canGoBack = false, canGoForward = false, tabCount = 1))
        rule.onNodeWithTag(BTN_BACK).assertIsNotEnabled()
        rule.onNodeWithTag(BTN_FORWARD).assertIsNotEnabled()
        rule.onNodeWithTag(BTN_HOME).assertIsEnabled()
        rule.onNodeWithTag(BTN_TABS).assertIsEnabled()
        rule.onNodeWithTag(BTN_MENU).assertIsEnabled()
    }

    @Test
    fun ac15_canGoBackAndForward_enablesButtons() {
        show(BrowserUiState(canGoBack = true, canGoForward = true, tabCount = 1))
        rule.onNodeWithTag(BTN_BACK).assertIsEnabled()
        rule.onNodeWithTag(BTN_FORWARD).assertIsEnabled()
    }

    // --- AC16 (stateless) ---

    @Test
    fun ac16_taps_invokeMatchingCallbacks() {
        show(BrowserUiState(canGoBack = true, canGoForward = true, tabCount = 1))
        buttons.forEach { rule.onNodeWithTag(it).performClick() }
        assertEquals(listOf("back", "forward", "home", "tabs", "menu"), calls)
    }

    @Test
    fun ac16_tapDisabledBackOrForward_doesNothing() {
        show(BrowserUiState(canGoBack = false, canGoForward = false, tabCount = 1))
        rule.onNodeWithTag(BTN_BACK).performClick()
        rule.onNodeWithTag(BTN_FORWARD).performClick()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun edge_doubleTapBack_callsOnBackOncePerTap() {
        show(BrowserUiState(canGoBack = true, tabCount = 1))
        rule.onNodeWithTag(BTN_BACK).performClick()
        rule.onNodeWithTag(BTN_BACK).performClick()
        assertEquals(listOf("back", "back"), calls)
    }

    // --- AC17 ---

    private fun badgeFor(count: Int): String {
        rule.setContent {
            BrowserBottomBar(
                state = BrowserUiState(tabCount = count),
                actions = actions,
                onTabsClick = {},
                onMenuClick = {},
            )
        }
        return rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).textValue()
    }

    @Test
    fun ac17_badge_one() = assertEquals("1", badgeFor(1))

    @Test
    fun ac17_badge_twelve() = assertEquals("12", badgeFor(12))

    @Test
    fun ac17_badge_ninetyNine() = assertEquals("99", badgeFor(99))

    @Test
    fun ac17_badge_hundred_isCapped() = assertEquals("99+", badgeFor(100))

    @Test
    fun ac17_badge_updatesWithState_andContentDescriptionContainsCount() {
        var state by mutableStateOf(BrowserUiState(tabCount = 1))
        rule.setContent {
            BrowserBottomBar(state = state, actions = actions, onTabsClick = {}, onMenuClick = {})
        }
        state = state.copy(tabCount = 12)
        rule.waitForIdle()
        assertEquals("12", rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).textValue())
        assertTrue(
            rule
                .onNodeWithTag(BTN_TABS)
                .contentDescriptions()
                .joinToString(" ")
                .contains("12"),
        )
    }

    // --- AC18–AC22 (stateless) ---

    @Test
    fun ac18_longPressBack_scrollsToTop_evenWhenDisabled_withoutBack() {
        show(BrowserUiState(canGoBack = false, tabCount = 1))
        rule.onNodeWithTag(BTN_BACK).performTouchInput { longClick() }
        assertEquals(listOf("top"), calls)
    }

    @Test
    fun ac18_longPressBack_whenEnabled_doesNotAlsoTap() {
        show(BrowserUiState(canGoBack = true, tabCount = 1))
        rule.onNodeWithTag(BTN_BACK).performTouchInput { longClick() }
        assertEquals(listOf("top"), calls)
    }

    @Test
    fun ac19_longPressForward_scrollsToBottom_evenWhenDisabled_withoutForward() {
        show(BrowserUiState(canGoForward = false, tabCount = 1))
        rule.onNodeWithTag(BTN_FORWARD).performTouchInput { longClick() }
        assertEquals(listOf("bottom"), calls)
    }

    @Test
    fun ac19_longPressForward_whenEnabled_doesNotAlsoTap() {
        show(BrowserUiState(canGoForward = true, tabCount = 1))
        rule.onNodeWithTag(BTN_FORWARD).performTouchInput { longClick() }
        assertEquals(listOf("bottom"), calls)
    }

    @Test
    fun ac20_longPressHome_startsEditing_withoutHome() {
        show()
        rule.onNodeWithTag(BTN_HOME).performTouchInput { longClick() }
        assertEquals(listOf("edit"), calls)
    }

    @Test
    fun ac21_longPressTabs_opensNewTab_withoutOpeningSheet() {
        show()
        rule.onNodeWithTag(BTN_TABS).performTouchInput { longClick() }
        assertEquals(listOf("newTab"), calls)
    }

    @Test
    fun ac22_longPressMenu_reloads_withoutOpeningMenu() {
        show()
        rule.onNodeWithTag(BTN_MENU).performTouchInput { longClick() }
        assertEquals(listOf("reload"), calls)
    }

    // --- AC23 ---

    private val longPressExpectations =
        listOf(
            BTN_BACK to "top",
            BTN_FORWARD to "bottom",
            BTN_HOME to "edit",
            BTN_TABS to "newTab",
            BTN_MENU to "reload",
        )

    @Test
    fun ac23_eachButton_hasLabeledCustomAccessibilityAction() {
        show()
        buttons.forEach { tag ->
            val labels = rule.onNodeWithTag(tag).customActionLabels()
            assertTrue("$tag custom action", labels.isNotEmpty() && labels.all { it.isNotBlank() })
        }
    }

    @Test
    fun ac23_customAction_matchesLongPress_evenWhenDisabled() {
        show(BrowserUiState(canGoBack = false, canGoForward = false, tabCount = 1))
        longPressExpectations.forEach { (tag, expected) ->
            calls.clear()
            rule.runOnIdle { assertTrue(rule.onNodeWithTag(tag).invokeFirstCustomAction()) }
            assertEquals(tag, listOf(expected), calls)
        }
    }

    @Test
    fun ac23_semanticsOnLongClick_matchesLongPress_evenWhenDisabled() {
        show(BrowserUiState(canGoBack = false, canGoForward = false, tabCount = 1))
        longPressExpectations.forEach { (tag, expected) ->
            calls.clear()
            rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnLongClick)
            assertEquals(tag, listOf(expected), calls)
        }
    }

    // --- Spec 011 AC9: ChromeButton dùng PixelIconButton ---

    @Test
    fun spec011_ac9_everyChromeButton_exposesCornerPercent_fromPixelIconButton() {
        show(BrowserUiState(canGoBack = true, canGoForward = false, tabCount = 1))
        buttons.forEach { tag ->
            val config = rule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertTrue("$tag phải là PixelIconButton (có CornerPercent)", PixelSemantics.CornerPercent in config)
            assertEquals("$tag khi nghỉ bo 50%", 50, config[PixelSemantics.CornerPercent])
        }
    }
}
