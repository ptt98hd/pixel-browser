package app.pixel.browser.feature.browser.gesture

import kotlin.math.abs

/** Quyết định đổi tab kề khi vuốt ngang trên bottom bar (spec 008 AC5). */
object TabSwipe {
    /** Chỉ số tab đích hoặc null (không đổi). Vuốt sang trái (dx < 0) = tab sau. */
    fun target(
        dxPx: Float,
        dyPx: Float,
        thresholdPx: Float,
        index: Int,
        count: Int,
    ): Int? {
        if (abs(dxPx) < thresholdPx || abs(dxPx) <= 2 * abs(dyPx)) return null
        val target = if (dxPx < 0) index + 1 else index - 1
        return target.takeIf { it in 0 until count }
    }
}
