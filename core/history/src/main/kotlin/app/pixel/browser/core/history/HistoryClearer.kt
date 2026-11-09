package app.pixel.browser.core.history

class HistoryClearer(
    private val store: HistoryStore,
    private val clock: () -> Long,
) {
    /** Xóa mọi lượt từ đầu [range] đến hiện tại (kể cả mốc tương lai do lệch đồng hồ); trả số dòng đã xóa. */
    suspend fun clear(range: HistoryRange): Int = store.deleteBetween(range.from(clock()), Long.MAX_VALUE)
}
