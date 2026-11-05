package app.pixel.browser.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Spec 011 AC10 – [PixelLoadingIndicator] vô định, 48dp, hình biến đổi theo thời gian. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PixelLoadingIndicatorTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(reducedMotion: Boolean = false) {
        // Animation vô hạn: tắt autoAdvance để test không chờ idle mãi.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                Box(Modifier.background(Color.White)) {
                    PixelLoadingIndicator(Modifier.testTag("l"))
                }
            }
        }
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
    }

    private fun pixels(): IntArray {
        val image = rule.onNodeWithTag("l").captureToImage()
        return IntArray(image.width * image.height).also { image.readPixels(it) }
    }

    @Test
    fun ac10_hasIndeterminateProgressSemantics() {
        show()
        val info = rule.onNodeWithTag("l").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(ProgressBarRangeInfo.Indeterminate, info)
    }

    @Test
    fun ac10_defaultSize_is48dp() {
        show()
        rule.onNodeWithTag("l").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun ac10_framesAnimate_twoCaptures300msApartDiffer() {
        show()
        val first = pixels()
        assertTrue("Indicator phải vẽ gì đó (ảnh không đồng màu)", first.distinct().size > 1)
        rule.mainClock.advanceTimeBy(300)
        val second = pixels()
        assertFalse("Hai ảnh cách 300ms phải khác nhau (hình biến đổi/xoay)", first.contentEquals(second))
    }

    @Test
    fun ac10_reducedMotion_twoCaptures300msApartAreIdentical() {
        show(reducedMotion = true)
        val first = pixels()
        assertTrue("Indicator phải vẽ gì đó (ảnh không đồng màu)", first.distinct().size > 1)
        rule.mainClock.advanceTimeBy(300)
        val second = pixels()
        assertTrue("Reduced motion: không animate", first.contentEquals(second))
    }
}
