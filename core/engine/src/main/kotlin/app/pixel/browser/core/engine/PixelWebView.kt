package app.pixel.browser.core.engine

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView

/**
 * WebView báo cuộn/overscroll về [WebViewEngineSession] (spec 008): `onScrollChanged` →
 * `dispatchScrollChanged`, `onOverScrolled` → `dispatchOverScrolled`. [owner] gán sau khi session khởi tạo và
 * gỡ trước khi `destroy()`.
 *
 * Fallback: nếu Chromium không báo overscroll khi kéo xuống ở đỉnh trang, [TopPullFallback] suy ra từ chạm
 * (xem [dispatchTouch]) và phát `dispatchOverScrolled(0, true)` một lần mỗi cử chỉ.
 */
class PixelWebView(
    context: Context,
) : WebView(context) {
    internal var owner: WebViewEngineSession? = null

    /** Session ẩn danh (spec 016): đề nghị bàn phím không học từ ô nhập. Đặt ngay sau khi tạo. */
    internal var isPrivate: Boolean = false
    private val fallback = TopPullFallback(FALLBACK_DP * context.resources.displayMetrics.density)
    private var lastY = 0f

    override fun onScrollChanged(
        l: Int,
        t: Int,
        oldl: Int,
        oldt: Int,
    ) {
        super.onScrollChanged(l, t, oldl, oldt)
        owner?.dispatchScrollChanged(scrollY)
    }

    override fun onOverScrolled(
        scrollX: Int,
        scrollY: Int,
        clampedX: Boolean,
        clampedY: Boolean,
    ) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (clampedY && scrollY == 0) fallback.onOverscroll()
        owner?.dispatchOverScrolled(scrollY, clampedY)
    }

    /** Session ẩn danh: đề nghị bàn phím không học từ ô nhập (spec 016 AC7). */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        if (isPrivate) outAttrs.imeOptions = privateImeOptions(outAttrs.imeOptions)
        return connection
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        dispatchTouch(event)
        return super.onTouchEvent(event)
    }

    private fun dispatchTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.y
                fallback.onDown(atTop = scrollY == 0 && !canScrollVertically(-1))
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastY
                lastY = event.y
                if (fallback.onMove(dy)) owner?.dispatchOverScrolled(0, true)
            }

            else -> {
                Unit
            }
        }
    }

    private companion object {
        const val FALLBACK_DP = 24f
    }
}
