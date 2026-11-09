package app.pixel.browser.feature.browser.gesture

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.tabs.EngineSessionFactory
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.tabs.TabSnapshot
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.BrowserFixture
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserTestTags.ENGINE_CONTAINER
import app.pixel.browser.feature.browser.BrowserTestTags.PULL_REFRESH_INDICATOR
import app.pixel.browser.feature.browser.BrowserViewModel
import app.pixel.browser.feature.browser.InMemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 008 AC15–AC17 – kéo để tải lại, dựa trên `topOverscroll` của engine (BrowserRoute + Fake). */
@RunWith(RobolectricTestRunner::class)
class PullToRefreshTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val url = "https://a.com/"
    private lateinit var f: BrowserFixture

    private fun launch() {
        f = BrowserFixture.onPage(url)
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
    }

    private fun engine() = rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true)

    private fun indicator() = rule.onNodeWithTag(PULL_REFRESH_INDICATOR, useUnmergedTree = true)

    private val isLoadingIndicator =
        hasAnyDescendant(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        )

    private fun SemanticsNodeInteraction.pressNearTop(extraDp: Float = 20f) =
        performTouchInput {
            down(topCenter + Offset(0f, 40.dp.toPx()))
            moveBy(Offset(0f, extraDp.dp.toPx()))
        }

    private fun SemanticsNodeInteraction.dragDown(dp: Float) = performTouchInput { moveBy(Offset(0f, dp.dp.toPx())) }

    private fun overscroll() = rule.runOnIdle { f.selected().fake.simulateOverscrollTop() }

    // --- AC15 ---

    @Test
    fun ac15_pullAtTop_showsIndicator_thenReleaseReloadsOnce_andIndicatorShowsLoadingUntilDone() {
        launch()
        engine().pressNearTop()
        indicator().assertDoesNotExist()
        overscroll()
        engine().dragDown(200f)
        indicator().assertExists()
        engine().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, f.selected().reloadCalls)
        assertTrue(
            f
                .selected()
                .fake.state.value.isLoading,
        )
        indicator().assertExists().assert(isLoadingIndicator)

        rule.runOnIdle { f.selected().fake.simulatePageFinished(url) }
        rule.waitForIdle()
        indicator().assertDoesNotExist()
    }

    // --- AC16 ---

    @Test
    fun ac16a_dragWithoutOverscroll_doesNotReload_noIndicator() {
        launch()
        engine().pressNearTop()
        engine().dragDown(200f)
        indicator().assertDoesNotExist()
        engine().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, f.selected().reloadCalls)
        indicator().assertDoesNotExist()
    }

    @Test
    fun ac16b_scrolledDownBeforeTouch_overscrollIsIgnored() {
        launch()
        rule.runOnIdle { f.selected().fake.simulateScroll(300) }
        rule.waitForIdle()
        engine().pressNearTop()
        overscroll()
        engine().dragDown(200f)
        indicator().assertDoesNotExist()
        engine().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, f.selected().reloadCalls)
    }

    @Test
    fun ac16c_shortPullAfterOverscroll_doesNotReload_andIndicatorRetracts() {
        launch()
        engine().pressNearTop()
        overscroll()
        engine().dragDown(150f)
        engine().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, f.selected().reloadCalls)
        indicator().assertDoesNotExist()
    }

    @Test
    fun ac16d_cancelledGesture_doesNotReload() {
        launch()
        engine().pressNearTop()
        overscroll()
        engine().dragDown(200f)
        engine().performTouchInput { cancel() }
        rule.waitForIdle()
        assertEquals(0, f.selected().reloadCalls)
        indicator().assertDoesNotExist()
    }

    /** View engine giả ghi lại mọi MotionEvent nó nhận (để kiểm observer không consume chạm). */
    private class RecordingView(
        context: Context,
    ) : FrameLayout(context) {
        val actions = mutableListOf<Int>()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            actions += event.actionMasked
            return true
        }
    }

    private class ViewSession(
        val fake: FakeEngineSession,
        override val view: View,
    ) : EngineSession by fake

    @Test
    fun ac16_touchEventsStillReachEngineView_downMoveUp() {
        val recording = RecordingView(rule.activity)
        lateinit var session: ViewSession
        val store =
            InMemoryStore().apply { stored = TabsSnapshot(listOf(TabSnapshot("page", url)), "page") }
        val manager =
            TabManager(
                EngineSessionFactory { ViewSession(FakeEngineSession(), recording).also { session = it } },
                store,
            )
        val vm = BrowserViewModel(manager)
        rule.setContent { BrowserRoute(viewModel = vm) }
        rule.waitForIdle()
        rule.runOnIdle { session.fake.simulatePageFinished(url) }

        engine().pressNearTop()
        rule.runOnIdle { session.fake.simulateOverscrollTop() }
        engine().dragDown(200f)
        engine().performTouchInput { up() }
        rule.waitForIdle()

        val seen = recording.actions
        assertEquals(seen.toString(), MotionEvent.ACTION_DOWN, seen.first())
        assertTrue(seen.toString(), MotionEvent.ACTION_MOVE in seen)
        assertEquals(seen.toString(), MotionEvent.ACTION_UP, seen.last())
        assertFalse(seen.toString(), MotionEvent.ACTION_CANCEL in seen)
    }

    // --- AC17 ---

    @Test
    fun ac17_editingUrl_pullDoesNotReload() {
        launch()
        rule.runOnIdle { f.viewModel.onStartEditing() }
        rule.waitForIdle()
        engine().pressNearTop()
        overscroll()
        engine().dragDown(200f)
        indicator().assertDoesNotExist()
        engine().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, f.selected().reloadCalls)
    }
}
