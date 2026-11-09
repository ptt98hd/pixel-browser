package app.pixel.browser.core.engine

/**
 * Fallback cho `topOverscroll` (spec 008): trên Android 12+ Chromium có thể không gọi `onOverScrolled` khi kéo quá
 * đỉnh. Nếu lúc `down` trang đang ở đỉnh và ngón tay kéo xuống tích lũy ≥ [thresholdPx] mà chưa có overscroll thật,
 * [onMove] trả true đúng một lần trong cử chỉ đó.
 */
class TopPullFallback(
    private val thresholdPx: Float,
) {
    private var armed = false
    private var dragged = 0f

    fun onDown(atTop: Boolean) {
        armed = atTop
        dragged = 0f
    }

    /** Overscroll thật đã được báo: fallback không bao giờ phát nữa trong cử chỉ này. */
    fun onOverscroll() {
        armed = false
    }

    /** [dyPx] dương = kéo xuống. true = nên phát `topOverscroll`. */
    fun onMove(dyPx: Float): Boolean {
        if (!armed) return false
        dragged = (dragged + dyPx).coerceAtLeast(0f)
        if (dragged < thresholdPx) return false
        armed = false
        return true
    }
}
