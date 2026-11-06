package app.pixel.browser.core.tabs

/** Ảnh chụp một tab (không bao giờ là tab ẩn danh). [engineState] là dữ liệu của `EngineSession.saveState()`. */
class TabSnapshot(
    val id: String,
    val url: String,
    val title: String = "",
    val engineState: ByteArray? = null,
    /** Spec 010: xem [Tab.backToHome]. */
    val backToHome: Boolean = false,
)

/** Ảnh chụp toàn bộ danh sách tab để khôi phục sau xoay màn hình/process death. */
class TabsSnapshot(
    val tabs: List<TabSnapshot>,
    val selectedTabId: String?,
)

/**
 * Nơi lưu [TabsSnapshot]. [read] trả về null nếu chưa có hoặc dữ liệu hỏng.
 * Cài đặt có thể làm I/O đĩa: gọi [read]/[write] ngoài main thread (vd. `Dispatchers.IO`).
 */
interface TabSnapshotStore {
    fun read(): TabsSnapshot?

    fun write(snapshot: TabsSnapshot)
}
