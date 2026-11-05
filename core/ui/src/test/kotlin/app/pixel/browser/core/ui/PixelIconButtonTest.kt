package app.pixel.browser.core.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 011 AC8 – shape morph 50% ↔ 30% của [PixelIconButton]. */
@RunWith(RobolectricTestRunner::class)
class PixelIconButtonTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0
    private var longClicks = 0

    private fun show(
        enabled: Boolean = true,
        reducedMotion: Boolean = false,
        interactionSource: MutableInteractionSource? = null,
        withLongClick: Boolean = true,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                PixelIconButton(
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("b"),
                    enabled = enabled,
                    onLongClick = if (withLongClick) ({ longClicks++ }) else null,
                    interactionSource = interactionSource,
                ) { Box(Modifier.size(24.dp)) }
            }
        }
        rule.waitForIdle()
    }

    private fun corner(): Int =
        rule
            .onNodeWithTag("b")
            .fetchSemanticsNode()
            .config[PixelSemantics.CornerPercent]

    /** Đẩy đồng hồ từng frame 16ms trong [millis], trả về CornerPercent sau mỗi frame. */
    private fun sampleFor(millis: Long): List<Int> =
        (0 until millis / FRAME_MS).map {
            rule.mainClock.advanceTimeBy(FRAME_MS)
            corner()
        }

    @Test
    fun ac8_atRest_cornerIs50Percent() {
        show()
        assertEquals(50, corner())
    }

    @Test
    fun ac8_press_animatesThroughIntermediateValues_to30() {
        show()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("b").performTouchInput { down(center) }

        val samples = sampleFor(500)
        assertTrue("Phải có giá trị trung gian trong (30, 50) – có animation: $samples", samples.any { it in 31..49 })
        assertEquals(30, corner())
    }

    /**
     * Giữ 500ms vượt long-press timeout (400ms) → nếu có onLongClick thì nhả ra là long-press, không phải click.
     * Vì vậy kịch bản AC8 "down + 500ms, up → onClick 1 lần" dùng nút không có onLongClick.
     */
    @Test
    fun ac8_release_animatesBackTo50_andClicksOnce() {
        show(withLongClick = false)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("b").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(30, corner())

        rule.onNodeWithTag("b").performTouchInput { up() }
        val samples = sampleFor(500)
        assertTrue("Thả ra cũng phải animate: $samples", samples.any { it in 31..49 })
        assertEquals(50, corner())
        assertEquals(1, clicks)
        assertEquals(0, longClicks)
    }

    @Test
    fun ac8_tap_withLongClickHandler_invokesOnClickOnce() {
        show()
        rule.onNodeWithTag("b").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
        assertEquals(0, longClicks)
    }

    @Test
    fun ac8_reducedMotion_jumpsDirectlyTo30_thenBackTo50() {
        show(reducedMotion = true)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("b").performTouchInput { down(center) }
        repeat(SNAP_FRAMES) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(30, corner())

        rule.onNodeWithTag("b").performTouchInput { up() }
        repeat(SNAP_FRAMES) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(50, corner())
        assertEquals(1, clicks)
    }

    @Test
    fun ac8_disabled_pressDoesNotMorph_norClick() {
        show(enabled = false)
        rule.onNodeWithTag("b").assertIsNotEnabled()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("b").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(50, corner())
        rule.onNodeWithTag("b").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(50, corner())
        assertEquals(0, clicks)
    }

    @Test
    fun ac8_disabled_longPress_stillInvokesOnLongClick() {
        show(enabled = false)
        rule.onNodeWithTag("b").assertIsNotEnabled()
        rule.onNodeWithTag("b").performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(1, longClicks)
        assertEquals(0, clicks)
    }

    @Test
    fun ac8_enabled_longPress_invokesOnLongClick_notOnClick() {
        show()
        rule.onNodeWithTag("b").performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(1, longClicks)
        assertEquals(0, clicks)
    }

    @Test
    fun ac8_externalInteractionSource_pressMorphsShape() {
        val source = MutableInteractionSource()
        show(interactionSource = source)
        val press = PressInteraction.Press(Offset.Zero)
        rule.runOnIdle { check(source.tryEmit(press)) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(30, corner())

        rule.runOnIdle { check(source.tryEmit(PressInteraction.Release(press))) }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(50, corner())
    }

    private companion object {
        const val FRAME_MS = 16L

        /** Snap phải xong trong vài frame (đổi state → recompose → snap); spring cần hàng trăm ms. */
        const val SNAP_FRAMES = 3
    }
}
