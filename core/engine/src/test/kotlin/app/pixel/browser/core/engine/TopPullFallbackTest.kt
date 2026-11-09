package app.pixel.browser.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 008 (Quyết định) – fallback khi Chromium không báo `onOverScrolled`. */
class TopPullFallbackTest {
    private fun fallback() = TopPullFallback(thresholdPx = 24f)

    @Test
    fun downwardDragAtTop_pastThreshold_firesOnce() {
        val f = fallback()
        f.onDown(atTop = true)
        assertFalse(f.onMove(10f))
        assertTrue(f.onMove(20f))
        assertFalse(f.onMove(50f))
    }

    @Test
    fun notAtTop_neverFires() {
        val f = fallback()
        f.onDown(atTop = false)
        assertFalse(f.onMove(500f))
    }

    @Test
    fun overscrollAlreadyReported_neverFires() {
        val f = fallback()
        f.onDown(atTop = true)
        f.onOverscroll()
        assertFalse(f.onMove(500f))
    }

    @Test
    fun upwardDrag_cancelsOutDownwardDrag() {
        val f = fallback()
        f.onDown(atTop = true)
        assertFalse(f.onMove(20f))
        assertFalse(f.onMove(-20f))
        assertFalse(f.onMove(20f))
        assertTrue(f.onMove(10f))
    }

    @Test
    fun newGesture_canFireAgain() {
        val f = fallback()
        f.onDown(atTop = true)
        assertTrue(f.onMove(30f))
        f.onDown(atTop = true)
        assertTrue(f.onMove(30f))
    }

    @Test
    fun moveBeforeAnyDown_doesNothing() {
        assertFalse(fallback().onMove(500f))
    }
}
