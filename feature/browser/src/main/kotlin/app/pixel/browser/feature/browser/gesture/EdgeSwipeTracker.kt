package app.pixel.browser.feature.browser.gesture

/** Theo dõi vuốt từ cạnh màn hình để back/forward (spec 008 AC3). */
class EdgeSwipeTracker(
    private val thresholdPx: Float,
    private val flingVelocityPx: Float,
) {
    private var dx = 0f

    /** Cộng dồn [dxPx] theo hướng vào trong màn hình (clamp ≥ 0). */
    fun onDrag(dxPx: Float) {
        dx = (dx + dxPx).coerceAtLeast(0f)
    }

    /** dx / threshold, clamp 0..1. */
    val progress: Float get() = (dx / thresholdPx).coerceIn(0f, 1f)

    /** `progress == 1`. */
    val isArmed: Boolean get() = progress >= 1f

    /** true = commit: đã đủ ngưỡng, hoặc vuốt nhanh (>= flingVelocity) với dx >= threshold / 3. */
    fun onRelease(velocityPx: Float): Boolean =
        isArmed || (velocityPx >= flingVelocityPx && dx >= thresholdPx / FLING_MIN_FRACTION)

    fun reset() {
        dx = 0f
    }

    private companion object {
        const val FLING_MIN_FRACTION = 3f
    }
}
