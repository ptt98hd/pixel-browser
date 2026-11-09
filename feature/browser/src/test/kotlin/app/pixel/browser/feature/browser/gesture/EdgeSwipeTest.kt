package app.pixel.browser.feature.browser.gesture

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pixel.browser.feature.browser.BrowserActions
import app.pixel.browser.feature.browser.BrowserScreen
import app.pixel.browser.feature.browser.BrowserTestTags.EDGE_SWIPE_INDICATOR
import app.pixel.browser.feature.browser.BrowserTestTags.EDGE_SWIPE_LEFT
import app.pixel.browser.feature.browser.BrowserTestTags.EDGE_SWIPE_RIGHT
import app.pixel.browser.feature.browser.BrowserTestTags.ENGINE_CONTAINER
import app.pixel.browser.feature.browser.BrowserUiState
import app.pixel.browser.feature.browser.EngineContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 008 AC9–AC11, AC17 – vuốt cạnh khi không có điều hướng cử chỉ ([BrowserScreen] + callback ghi lại). */
@RunWith(RobolectricTestRunner::class)
class EdgeSwipeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val actions =
        BrowserActions(
            onBack = { calls += "back" },
            onForward = { calls += "forward" },
        )

    private fun show(
        state: BrowserUiState,
        systemGestureNav: Boolean? = false,
    ) {
        rule.setContent {
            if (systemGestureNav == null) {
                BrowserScreen(state = state, actions = actions, engineContent = { EngineContainer(view = null) })
            } else {
                BrowserScreen(
                    state = state,
                    actions = actions,
                    engineContent = { EngineContainer(view = null) },
                    systemGestureNav = systemGestureNav,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun state(
        canGoBack: Boolean = true,
        canGoForward: Boolean = true,
        isEditingUrl: Boolean = false,
    ) = BrowserUiState(
        url = "https://a.com/",
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        tabCount = 1,
        isEditingUrl = isEditingUrl,
    )

    private fun left() = rule.onNodeWithTag(EDGE_SWIPE_LEFT, useUnmergedTree = true)

    private fun right() = rule.onNodeWithTag(EDGE_SWIPE_RIGHT, useUnmergedTree = true)

    private fun indicator() = rule.onNodeWithTag(EDGE_SWIPE_INDICATOR, useUnmergedTree = true)

    // --- AC9 ---

    @Test
    fun ac9_onlyBackAvailable_hasLeftStripOnly() {
        show(state(canGoBack = true, canGoForward = false))
        left().assertExists()
        right().assertDoesNotExist()
    }

    @Test
    fun ac9_onlyForwardAvailable_hasRightStripOnly() {
        show(state(canGoBack = false, canGoForward = true))
        right().assertExists()
        left().assertDoesNotExist()
    }

    @Test
    fun ac9_neither_hasNoStrips() {
        show(state(canGoBack = false, canGoForward = false))
        left().assertDoesNotExist()
        right().assertDoesNotExist()
    }

    @Test
    fun ac9_systemGestureNav_hasNoStrips_evenWhenNavigable() {
        show(state(), systemGestureNav = true)
        left().assertDoesNotExist()
        right().assertDoesNotExist()
    }

    @Test
    fun ac9_defaultParameter_isThreeButtonNavInRobolectric() {
        show(state(), systemGestureNav = null)
        left().assertExists()
        right().assertExists()
    }

    @Test
    fun ac9_strips_are20dpWide_andFlushWithEngineContainerEdges() {
        show(state())
        left().assertWidthIsEqualTo(20.dp)
        right().assertWidthIsEqualTo(20.dp)
        val engine = rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val l = left().fetchSemanticsNode().boundsInRoot
        val r = right().fetchSemanticsNode().boundsInRoot
        assertEquals(engine.left, l.left, 0.5f)
        assertEquals(engine.right, r.right, 0.5f)
    }

    // --- AC10 ---

    @Test
    fun ac10_leftDragPastThreshold_goesBackOnce_andHidesIndicator() {
        show(state())
        left().performTouchInput {
            down(Offset(1f, centerY))
            moveBy(Offset(60.dp.toPx(), 0f))
        }
        indicator().assertExists()
        assertTrue(calls.isEmpty())
        left().performTouchInput {
            moveBy(Offset(60.dp.toPx(), 0f))
            up()
        }
        assertEquals(listOf("back"), calls)
        indicator().assertDoesNotExist()
    }

    @Test
    fun ac10_leftShortSlowDrag_doesNotGoBack() {
        show(state())
        left().performTouchInput {
            down(Offset(1f, centerY))
            moveBy(Offset(60.dp.toPx(), 0f), delayMillis = 500)
            advanceEventTime(500)
            up()
        }
        assertTrue(calls.isEmpty())
        indicator().assertDoesNotExist()
    }

    /**
     * Spec ghi `swipeRight(durationMillis = 50)` dài 40dp = 800dp/s, thấp hơn `EDGE_FLING_VELOCITY` 1000dp/s;
     * test dùng 30ms (~1333dp/s) cho nhất quán với ngưỡng đã chốt (xem báo cáo – chênh lệch spec).
     */
    @Test
    fun ac10_leftFastShortFling_goesBack() {
        show(state())
        left().performTouchInput {
            swipe(start = Offset(1f, centerY), end = Offset(1f + 40.dp.toPx(), centerY), durationMillis = 30)
        }
        assertEquals(listOf("back"), calls)
    }

    // --- AC11 ---

    @Test
    fun ac11_rightDragPastThreshold_goesForwardOnce() {
        show(state())
        right().performTouchInput {
            down(Offset(right - 1f, centerY))
            moveBy(Offset(-60.dp.toPx(), 0f))
        }
        indicator().assertExists()
        assertTrue(calls.isEmpty())
        right().performTouchInput {
            moveBy(Offset(-60.dp.toPx(), 0f))
            up()
        }
        assertEquals(listOf("forward"), calls)
        indicator().assertDoesNotExist()
    }

    @Test
    fun ac11_rightShortSlowDrag_doesNothing() {
        show(state())
        right().performTouchInput {
            down(Offset(right - 1f, centerY))
            moveBy(Offset(-60.dp.toPx(), 0f), delayMillis = 500)
            advanceEventTime(500)
            up()
        }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun ac11_verticalDragInsideStrip_doesNothing() {
        show(state())
        left().performTouchInput {
            down(Offset(1f, centerY))
            moveBy(Offset(4.dp.toPx(), 150.dp.toPx()))
            up()
        }
        right().performTouchInput {
            down(Offset(right - 1f, centerY))
            moveBy(Offset(-4.dp.toPx(), 150.dp.toPx()))
            up()
        }
        assertTrue(calls.isEmpty())
        indicator().assertDoesNotExist()
    }

    // --- AC17 ---

    @Test
    fun ac17_editingUrl_hasNoStrips() {
        show(state(isEditingUrl = true))
        left().assertDoesNotExist()
        right().assertDoesNotExist()
    }

    // --- RTL: dải theo vị trí vật lý, back ở mép phải ---

    private fun showRtl(state: BrowserUiState) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BrowserScreen(
                    state = state,
                    actions = actions,
                    engineContent = { EngineContainer(view = null) },
                    systemGestureNav = false,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun rtl_backStripIsOnPhysicalRightEdge_andSwipingLeftGoesBack() {
        showRtl(state(canGoBack = true, canGoForward = false))
        left().assertDoesNotExist()
        val engine = rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val r = right().fetchSemanticsNode().boundsInRoot
        assertEquals(engine.right, r.right, 0.5f)
        right().performTouchInput {
            down(Offset(right - 1f, centerY))
            moveBy(Offset(-60.dp.toPx(), 0f))
            moveBy(Offset(-60.dp.toPx(), 0f))
            up()
        }
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun rtl_forwardStripIsOnPhysicalLeftEdge_andSwipingRightGoesForward() {
        showRtl(state(canGoBack = false, canGoForward = true))
        right().assertDoesNotExist()
        val engine = rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(engine.left, left().fetchSemanticsNode().boundsInRoot.left, 0.5f)
        left().performTouchInput {
            down(Offset(1f, centerY))
            moveBy(Offset(60.dp.toPx(), 0f))
            moveBy(Offset(60.dp.toPx(), 0f))
            up()
        }
        assertEquals(listOf("forward"), calls)
    }

    @Test
    fun rtl_indicatorAppearsOnTheSideOfTheDraggedEdge() {
        showRtl(state(canGoBack = true, canGoForward = false))
        right().performTouchInput {
            down(Offset(right - 1f, centerY))
            moveBy(Offset(-60.dp.toPx(), 0f))
        }
        val root = rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            indicator()
                .fetchSemanticsNode()
                .boundsInRoot.center.x > root.center.x,
        )
    }
}
