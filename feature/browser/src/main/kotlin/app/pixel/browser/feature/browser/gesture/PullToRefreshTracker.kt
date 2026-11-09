package app.pixel.browser.feature.browser.gesture

/** Theo dõi kéo-để-tải-lại dựa trên overscroll của root (spec 008 AC4). */
class PullToRefreshTracker(
    private val thresholdPx: Float,
    private val maxDistancePx: Float,
) {
    private var atTop = false
    private var overscrolled = false
    private var pulled = 0f
    private var cancelled = false

    fun onDown(isAtTop: Boolean) {
        atTop = isAtTop
        overscrolled = false
        pulled = 0f
        cancelled = false
    }

    /** [dyPx] dương = kéo xuống; chỉ tính sau [onTopOverscroll]. */
    fun onMove(dyPx: Float) {
        if (!overscrolled) return
        pulled = (pulled + dyPx).coerceAtLeast(0f)
    }

    fun onTopOverscroll() {
        if (atTop && !cancelled) overscrolled = true
    }

    /** Khoảng hiển thị indicator (đã nhân hệ số cản 0.5, clamp maxDistance). */
    val distancePx: Float
        get() = if (isActive) (pulled * RESISTANCE).coerceAtMost(maxDistancePx) else 0f

    val isActive: Boolean get() = overscrolled

    /** true = reload. Kết thúc cử chỉ. */
    fun onUp(): Boolean {
        val reload = isActive && distancePx >= thresholdPx
        overscrolled = false
        pulled = 0f
        atTop = false
        return reload
    }

    fun onCancel() {
        overscrolled = false
        pulled = 0f
        cancelled = true
    }

    private companion object {
        const val RESISTANCE = 0.5f
    }
}
