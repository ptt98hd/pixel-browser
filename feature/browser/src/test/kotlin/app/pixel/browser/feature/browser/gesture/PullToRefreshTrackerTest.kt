package app.pixel.browser.feature.browser.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 008 AC4 – [PullToRefreshTracker]. */
class PullToRefreshTrackerTest {
    private fun tracker() = PullToRefreshTracker(thresholdPx = 80f, maxDistancePx = 128f)

    @Test
    fun ac4_dragBeforeOverscroll_isInactive_withZeroDistance() {
        val t = tracker()
        t.onDown(isAtTop = true)
        t.onMove(100f)
        assertFalse(t.isActive)
        assertEquals(0f, t.distancePx, 0.001f)
    }

    @Test
    fun ac4_dragAfterOverscroll_showsHalfDistance_andReleaseReloads() {
        val t = tracker()
        t.onDown(isAtTop = true)
        t.onMove(100f)
        t.onTopOverscroll()
        t.onMove(200f)
        assertTrue(t.isActive)
        assertEquals(100f, t.distancePx, 0.001f)
        assertTrue(t.onUp())
    }

    @Test
    fun ac4_shortDragAfterOverscroll_doesNotReload() {
        val t = tracker()
        t.onDown(isAtTop = true)
        t.onTopOverscroll()
        t.onMove(120f)
        assertEquals(60f, t.distancePx, 0.001f)
        assertFalse(t.onUp())
    }

    @Test
    fun ac4_distance_isClampedToMax() {
        val t = tracker()
        t.onDown(isAtTop = true)
        t.onTopOverscroll()
        t.onMove(1000f)
        assertEquals(128f, t.distancePx, 0.001f)
    }

    @Test
    fun ac4_notAtTopOnDown_overscrollAndDragNeverActivate() {
        val t = tracker()
        t.onDown(isAtTop = false)
        t.onTopOverscroll()
        t.onMove(400f)
        assertFalse(t.isActive)
        assertEquals(0f, t.distancePx, 0.001f)
        assertFalse(t.onUp())
    }

    @Test
    fun ac4_draggingBackUp_reducesDistance_neverBelowZero() {
        val t = tracker()
        t.onDown(isAtTop = true)
        t.onTopOverscroll()
        t.onMove(200f)
        t.onMove(-60f)
        assertEquals(70f, t.distancePx, 0.001f)
        t.onMove(-1000f)
        assertEquals(0f, t.distancePx, 0.001f)
    }

    @Test
    fun ac4_cancel_deactivates_andUpDoesNotReload() {
        val t = tracker()
        t.onDown(isAtTop = true)
        t.onTopOverscroll()
        t.onMove(300f)
        t.onCancel()
        assertFalse(t.isActive)
        assertFalse(t.onUp())
    }

    @Test
    fun ac4_onlyDragAfterOverscrollCounts() {
        val t = tracker()
        t.onDown(isAtTop = true)
        t.onMove(300f)
        t.onTopOverscroll()
        t.onMove(100f)
        assertEquals(50f, t.distancePx, 0.001f)
        assertFalse(t.onUp())
    }

    @Test
    fun ac4_newGesture_startsClean() {
        val t = tracker()
        t.onDown(isAtTop = true)
        t.onTopOverscroll()
        t.onMove(300f)
        assertTrue(t.onUp())
        t.onDown(isAtTop = true)
        assertFalse(t.isActive)
        assertEquals(0f, t.distancePx, 0.001f)
    }
}
