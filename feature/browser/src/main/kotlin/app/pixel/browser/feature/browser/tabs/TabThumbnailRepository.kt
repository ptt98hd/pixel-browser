package app.pixel.browser.feature.browser.tabs

import app.pixel.browser.core.engine.EngineSession

/** Kho thumbnail của tab (spec 009). Không chứa kiểu Android để `BrowserViewModel` test được trên JVM. */
interface TabThumbnailRepository {
    /** Chụp trang của [session]; [persist] = `!tab.isPrivate` (tab ẩn danh không bao giờ ghi đĩa). */
    fun capture(
        tabId: String,
        session: EngineSession,
        persist: Boolean,
    )

    /** Xóa file trên đĩa; bản trong bộ nhớ giữ lại (phục vụ undo). */
    fun remove(tabId: String)

    /** Xóa file mồ côi (gọi một lần khi khởi tạo ViewModel). */
    fun retainOnly(tabIds: Set<String>)

    fun clearDisk()

    /** Không làm gì (mặc định của `BrowserViewModel`, test, preview). */
    object None : TabThumbnailRepository {
        override fun capture(
            tabId: String,
            session: EngineSession,
            persist: Boolean,
        ) = Unit

        override fun remove(tabId: String) = Unit

        override fun retainOnly(tabIds: Set<String>) = Unit

        override fun clearDisk() = Unit
    }
}
