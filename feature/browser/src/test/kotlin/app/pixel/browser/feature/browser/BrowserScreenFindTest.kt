package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.BACK_GESTURE_INDICATOR
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.FIND_BAR
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_FIND
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER
import app.pixel.browser.feature.browser.gesture.GestureGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 015 AC10 – menu "Tìm trong trang", thanh tìm trong màn hình, Back và cổng cử chỉ. */
@RunWith(RobolectricTestRunner::class)
class BrowserScreenFindTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var opened = 0
    private var closedFind = 0
    private var backs = 0
    private var cancelledEditing = 0

    private val page = BrowserUiState(url = "https://a.com/", displayHost = "a.com", tabCount = 1, canFind = true)
    private val bar = FindUiState("kot", 1, 3, false)

    private var state by mutableStateOf(page)

    private val actions =
        BrowserActions(
            onOpenFind = { opened++ },
            onCloseFind = {
                closedFind++
                state = state.copy(find = null)
            },
            onBack = { backs++ },
            onCancelEditing = {
                cancelledEditing++
                state = state.copy(isEditingUrl = false)
            },
        )

    private fun show(initial: BrowserUiState) {
        state = initial
        rule.setContent { BrowserScreen(state = state, actions = actions) }
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    // --- menu ---

    @Test
    fun ac10_menuFind_disabledWhenCannotFind() {
        show(page.copy(canFind = false))
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_FIND).assertExists().assertIsNotEnabled()
    }

    @Test
    fun ac10_menuFind_enabled_tapOpensFind_andClosesMenu() {
        show(page)
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_FIND).assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(1, opened)
        rule.onNodeWithTag(MENU_FIND).assertDoesNotExist()
    }

    // --- thanh tìm trong màn hình ---

    @Test
    fun ac10_findBar_shownOnlyWhileStateHasFind() {
        show(page)
        rule.onNodeWithTag(FIND_BAR).assertDoesNotExist()
        state = page.copy(find = bar)
        rule.waitForIdle()
        rule.onNodeWithTag(FIND_BAR).assertExists()
        state = page
        rule.waitForIdle()
        rule.onNodeWithTag(FIND_BAR).assertDoesNotExist()
    }

    // --- Back ---

    @Test
    fun ac10_back_withBarOpen_closesBar_notGoBack_noIndicator_thenSecondBackGoesBack() {
        show(page.copy(find = bar, canGoBack = true))
        pressBack()
        assertEquals(1, closedFind)
        assertEquals(0, backs)
        rule.onNodeWithTag(BACK_GESTURE_INDICATOR).assertDoesNotExist()
        rule.onNodeWithTag(FIND_BAR).assertDoesNotExist()
        pressBack()
        assertEquals(1, backs)
    }

    @Test
    fun ac10_back_withBarOpen_andNothingToGoBackTo_stillClosesBar() {
        show(page.copy(find = bar, canGoBack = false))
        pressBack()
        assertEquals(1, closedFind)
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun ac10_back_menuIsClosedBeforeFindBar() {
        show(page.copy(find = bar, canGoBack = true))
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_FIND).assertExists()
        pressBack()
        rule.onNodeWithTag(MENU_FIND).assertDoesNotExist()
        assertEquals(0, closedFind)
        rule.onNodeWithTag(FIND_BAR).assertExists()
        pressBack()
        assertEquals(1, closedFind)
        assertEquals(0, backs)
    }

    @Test
    fun ac10_back_tabSwitcherIsClosedBeforeFindBar() {
        show(page.copy(find = bar, canGoBack = true))
        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
        pressBack()
        rule.onNodeWithTag(TAB_SWITCHER).assertDoesNotExist()
        assertEquals(0, closedFind)
        assertEquals(0, backs)
    }

    @Test
    fun ac10_back_findBarIsClosedBeforeUrlEditing() {
        show(page.copy(find = bar, isEditingUrl = true, canGoBack = true))
        pressBack()
        assertEquals(1, closedFind)
        assertEquals(0, cancelledEditing)
        assertEquals(0, backs)
    }

    // --- cổng cử chỉ ---

    @Test
    fun ac10_gestures_edgeSwipeAndPullToRefresh_areOff_whileBarOpen() {
        val withHistory = page.copy(canGoBack = true, canGoForward = true)
        val open = withHistory.copy(find = bar)
        val before = GestureGate.of(withHistory, overlayOpen = false, systemGestureNav = false)
        assertTrue(before.edgeBack && before.edgeForward && before.pullToRefresh)
        val during = GestureGate.of(open, overlayOpen = false, systemGestureNav = false)
        assertFalse(during.edgeBack)
        assertFalse(during.edgeForward)
        assertFalse(during.pullToRefresh)
    }
}
