package app.pixel.browser.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 010 – AC22 (phần JVM): màu chữ/icon trên nền đơn sắc. */
class HomeContentColorTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun ac22_whiteBackground_blackContent() {
        assertEquals(black, homeContentColor(0xFFFFFFFF.toInt()))
    }

    @Test
    fun ac22_blackBackground_whiteContent() {
        assertEquals(white, homeContentColor(0xFF000000.toInt()))
    }

    /** Orchestrator: theo WCAG 2.x, #1E88E5 có L ≈ 0.214 → tương phản với đen ≈ 5.3 > với trắng ≈ 4.0 → đen. */
    @Test
    fun ac22_blue1E88E5_blackContent() {
        assertEquals(black, homeContentColor(0xFF1E88E5.toInt()))
    }

    @Test
    fun ac22_yellowFFEB3B_blackContent() {
        assertEquals(black, homeContentColor(0xFFFFEB3B.toInt()))
    }
}
