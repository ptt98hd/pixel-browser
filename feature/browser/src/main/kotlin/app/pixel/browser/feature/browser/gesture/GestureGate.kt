package app.pixel.browser.feature.browser.gesture

import app.pixel.browser.feature.browser.BrowserUiState

data class GestureAvailability(
    val edgeBack: Boolean,
    val edgeForward: Boolean,
    val tabSwipe: Boolean,
    val pullToRefresh: Boolean,
)

/** Cử chỉ nào đang được phép theo ngữ cảnh (spec 008 AC6, AC17). */
object GestureGate {
    fun of(
        state: BrowserUiState,
        overlayOpen: Boolean,
        systemGestureNav: Boolean,
    ): GestureAvailability {
        val blocked = overlayOpen || state.isEditingUrl || state.isFullscreen || state.pendingRequest != null
        val findOpen = state.find != null
        if (blocked) return GestureAvailability(false, false, false, false)
        return GestureAvailability(
            edgeBack = !systemGestureNav && state.canGoBack && !findOpen,
            edgeForward = !systemGestureNav && state.canGoForward && !findOpen,
            tabSwipe = state.tabCount > 1,
            // Spec 015: tải lại sẽ xóa kết quả tìm nên tắt khi thanh tìm mở.
            pullToRefresh = !state.isHome && !findOpen,
        )
    }
}
