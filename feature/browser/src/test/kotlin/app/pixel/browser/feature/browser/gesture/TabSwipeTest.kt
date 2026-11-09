package app.pixel.browser.feature.browser.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 008 AC5 – [TabSwipe.target]. */
class TabSwipeTest {
    private fun target(
        dx: Float,
        dy: Float = 0f,
        index: Int = 1,
        count: Int = 3,
    ) = TabSwipe.target(dx, dy, thresholdPx = 72f, index = index, count = count)

    @Test
    fun ac5_swipeLeft_selectsNextTab() = assertEquals(2, target(dx = -80f, dy = 10f))

    @Test
    fun ac5_swipeRight_selectsPreviousTab() = assertEquals(0, target(dx = 80f))

    @Test
    fun ac5_belowThreshold_isNull() = assertNull(target(dx = -60f))

    @Test
    fun ac5_tooVertical_isNull() = assertNull(target(dx = -80f, dy = 50f))

    @Test
    fun ac5_lastTab_swipeLeft_isNull() = assertNull(target(dx = -80f, index = 2))

    @Test
    fun ac5_firstTab_swipeRight_isNull() = assertNull(target(dx = 80f, index = 0))

    @Test
    fun ac5_singleTab_isNull() {
        assertNull(target(dx = -80f, index = 0, count = 1))
        assertNull(target(dx = 80f, index = 0, count = 1))
    }

    @Test
    fun ac5_verticalDirectionNeedsStrictlyMoreThanTwiceDy() = assertNull(target(dx = -80f, dy = 40f))
}
