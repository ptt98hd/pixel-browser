package app.pixel.browser.feature.browser.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 008 AC3 – [EdgeSwipeTracker]. */
class EdgeSwipeTrackerTest {
    private fun tracker() = EdgeSwipeTracker(thresholdPx = 100f, flingVelocityPx = 1000f)

    @Test
    fun ac3_halfwayDrag_hasHalfProgress_andIsNotArmed() {
        val t = tracker()
        t.onDrag(50f)
        assertEquals(0.5f, t.progress, 0.001f)
        assertFalse(t.isArmed)
    }

    @Test
    fun ac3_dragAccumulates_pastThreshold_isArmed_andReleaseCommits() {
        val t = tracker()
        t.onDrag(50f)
        t.onDrag(80f)
        assertEquals(1f, t.progress, 0.001f)
        assertTrue(t.isArmed)
        assertTrue(t.onRelease(0f))
    }

    @Test
    fun ac3_dragPastThenBackBelowThreshold_releaseDoesNotCommit() {
        val t = tracker()
        t.onDrag(120f)
        t.onDrag(-60f)
        assertFalse(t.onRelease(0f))
    }

    @Test
    fun ac3_fling_commitsWhenDragIsAtLeastThirdOfThreshold() {
        val t = tracker()
        t.onDrag(40f)
        assertTrue(t.onRelease(1500f))
    }

    @Test
    fun ac3_fling_doesNotCommitWhenDragIsTooShort() {
        val t = tracker()
        t.onDrag(20f)
        assertFalse(t.onRelease(5000f))
    }

    @Test
    fun ac3_slowRelease_belowThreshold_doesNotCommit() {
        val t = tracker()
        t.onDrag(60f)
        assertFalse(t.onRelease(0f))
    }

    @Test
    fun ac3_negativeDx_isClampedToZero() {
        val t = tracker()
        t.onDrag(-50f)
        assertEquals(0f, t.progress, 0.001f)
        assertFalse(t.onRelease(5000f))
    }

    @Test
    fun ac3_progress_isClampedToOne() {
        val t = tracker()
        t.onDrag(500f)
        assertEquals(1f, t.progress, 0.001f)
    }

    @Test
    fun ac3_reset_clearsProgress() {
        val t = tracker()
        t.onDrag(80f)
        t.reset()
        assertEquals(0f, t.progress, 0.001f)
        assertFalse(t.isArmed)
        assertFalse(t.onRelease(0f))
    }
}
