package app.pixel.browser.feature.browser.gesture

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import app.pixel.browser.feature.browser.BrowserFixture
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserTestTags.BACK_GESTURE_INDICATOR
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_RELOAD
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER
import app.pixel.browser.feature.browser.BrowserTestTags.URL_DISPLAY
import app.pixel.browser.feature.browser.BrowserTestTags.URL_FIELD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 008 AC7–AC8 – predictive back + mũi tên `back_gesture_indicator` (BrowserRoute + Fake). */
@RunWith(RobolectricTestRunner::class)
class BackGestureTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture

    /** Tab trang web có lịch sử a → b (canGoBack = true). */
    private fun launchWithHistory() {
        f = BrowserFixture.onPage("https://a.com/")
        f.selected().visit("https://www.b.com/")
        f.selected().resetCalls()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
    }

    private fun event(
        progress: Float,
        edge: Int,
    ) = BackEventCompat(touchX = 10f, touchY = 100f, progress = progress, swipeEdge = edge)

    private fun dispatcher() = rule.activity.onBackPressedDispatcher

    private fun backStarted(edge: Int = BackEventCompat.EDGE_LEFT) {
        rule.runOnIdle { dispatcher().dispatchOnBackStarted(event(0f, edge)) }
        rule.waitForIdle()
    }

    private fun backProgressed(
        progress: Float,
        edge: Int = BackEventCompat.EDGE_LEFT,
    ) {
        rule.runOnIdle { dispatcher().dispatchOnBackProgressed(event(progress, edge)) }
        rule.waitForIdle()
    }

    private fun backCancelled() {
        rule.runOnIdle { dispatcher().dispatchOnBackCancelled() }
        rule.waitForIdle()
    }

    private fun commitBack() {
        rule.runOnIdle { dispatcher().onBackPressed() }
        rule.waitForIdle()
    }

    private fun indicatorBounds() =
        rule.onNodeWithTag(BACK_GESTURE_INDICATOR, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun rootWidth() =
        rule
            .onRoot()
            .fetchSemanticsNode()
            .size.width
            .toFloat()

    private fun assertIndicatorHidden() =
        rule.onNodeWithTag(BACK_GESTURE_INDICATOR, useUnmergedTree = true).assertDoesNotExist()

    // --- AC7 ---

    @Test
    fun ac7_leftEdgeBackProgress_showsIndicatorOnLeftHalf_withoutGoBack() {
        launchWithHistory()
        backStarted(BackEventCompat.EDGE_LEFT)
        backProgressed(0.5f, BackEventCompat.EDGE_LEFT)
        rule.onNodeWithTag(BACK_GESTURE_INDICATOR, useUnmergedTree = true).assertExists()
        assertTrue(indicatorBounds().left < rootWidth() / 2)
        assertEquals(0, f.selected().goBackCalls)
    }

    @Test
    fun ac7_rightEdgeBackProgress_showsIndicatorOnRightHalf() {
        launchWithHistory()
        backStarted(BackEventCompat.EDGE_RIGHT)
        backProgressed(0.5f, BackEventCompat.EDGE_RIGHT)
        rule.onNodeWithTag(BACK_GESTURE_INDICATOR, useUnmergedTree = true).assertExists()
        assertTrue(indicatorBounds().center.x > rootWidth() / 2)
    }

    @Test
    fun ac7_cancelled_hidesIndicator_withoutGoBack() {
        launchWithHistory()
        backStarted()
        backProgressed(0.5f)
        backCancelled()
        assertIndicatorHidden()
        assertEquals(0, f.selected().goBackCalls)
    }

    @Test
    fun ac7_commit_goesBackExactlyOnce_andHidesIndicator() {
        launchWithHistory()
        backStarted()
        backProgressed(0.5f)
        commitBack()
        assertEquals(1, f.selected().goBackCalls)
        assertIndicatorHidden()
    }

    @Test
    fun ac7_cancelThenRestart_thenCommit_goesBackOnce() {
        launchWithHistory()
        backStarted()
        backProgressed(0.4f)
        backCancelled()
        backStarted(BackEventCompat.EDGE_RIGHT)
        backProgressed(0.7f, BackEventCompat.EDGE_RIGHT)
        commitBack()
        assertEquals(1, f.selected().goBackCalls)
        assertIndicatorHidden()
    }

    @Test
    fun ac7_indicatorGrowsWithProgress() {
        launchWithHistory()
        backStarted()
        backProgressed(0.2f)
        val small = indicatorBounds().width
        backProgressed(1f)
        val large = indicatorBounds().width
        assertTrue("indicator $small -> $large", large > small)
    }

    // --- AC8: thứ tự AC31 giữ nguyên, không có indicator cho các nhánh khác ---

    @Test
    fun ac8_menuOpen_noIndicator_commitClosesMenu_withoutGoBack() {
        launchWithHistory()
        rule.onNodeWithTag(BTN_MENU).performClick()
        backStarted()
        backProgressed(0.5f)
        assertIndicatorHidden()
        commitBack()
        rule.onNodeWithTag(MENU_RELOAD).assertDoesNotExist()
        assertEquals(0, f.selected().goBackCalls)
    }

    @Test
    fun ac8_tabSwitcherOpen_noIndicator_commitClosesSwitcher_withoutGoBack() {
        launchWithHistory()
        rule.onNodeWithTag(BTN_TABS).performClick()
        backStarted()
        backProgressed(0.5f)
        assertIndicatorHidden()
        commitBack()
        rule.onNodeWithTag(TAB_SWITCHER).assertDoesNotExist()
        assertEquals(0, f.selected().goBackCalls)
    }

    @Test
    fun ac8_editingUrl_noIndicator_commitCancelsEditing_withoutGoBack() {
        launchWithHistory()
        rule.onNodeWithTag(URL_DISPLAY).performClick()
        rule.onNodeWithTag(URL_FIELD).assertExists()
        backStarted()
        backProgressed(0.5f)
        assertIndicatorHidden()
        commitBack()
        rule.onNodeWithTag(URL_FIELD).assertDoesNotExist()
        assertFalse(f.state().isEditingUrl)
        assertEquals(0, f.selected().goBackCalls)
    }

    @Test
    fun ac8_fullscreen_noIndicator_commitExitsFullscreen_withoutGoBack() {
        launchWithHistory()
        rule.runOnIdle { f.selected().fake.simulateEnterFullscreen() }
        rule.waitForIdle()
        backStarted()
        backProgressed(0.5f)
        assertIndicatorHidden()
        commitBack()
        assertEquals(1, f.selected().fake.exitFullscreenCalls)
        assertEquals(0, f.selected().goBackCalls)
    }

    @Test
    fun ac8_nothingToGoBackTo_noIndicator_handlerDisabled() {
        f = BrowserFixture()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { assertFalse(dispatcher().hasEnabledCallbacks()) }
        backStarted()
        assertIndicatorHidden()
        assertEquals(0, f.selected().goBackCalls)
    }

    // --- RTL: swipeEdge là vị trí vật lý ---

    @Test
    fun rtl_leftEdgeIndicatorStaysOnPhysicalLeft_rightOnRight() {
        f = BrowserFixture.onPage("https://a.com/")
        f.selected().visit("https://www.b.com/")
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BrowserRoute(viewModel = f.viewModel)
            }
        }
        rule.waitForIdle()
        backStarted(BackEventCompat.EDGE_LEFT)
        backProgressed(0.5f, BackEventCompat.EDGE_LEFT)
        assertTrue(indicatorBounds().center.x < rootWidth() / 2)
        backCancelled()
        backStarted(BackEventCompat.EDGE_RIGHT)
        backProgressed(0.5f, BackEventCompat.EDGE_RIGHT)
        assertTrue(indicatorBounds().center.x > rootWidth() / 2)
    }
}
