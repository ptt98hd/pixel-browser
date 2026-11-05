package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.isValidWebUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Quản lý danh sách tab và [EngineSession] của chúng (spec 002). Chỉ dùng trên main thread.
 *
 * Khi khởi tạo, khôi phục từ [store]; nếu không có/hỏng thì tạo một tab home được chọn.
 * Khi số session sống vượt [maxLiveSessions], tab nền ít được chọn gần đây nhất bị discard
 * (giữ snapshot, đóng session); chọn lại tab đó sẽ tạo session mới và `restoreState`.
 *
 * URL/tiêu đề trong [tabs] được đồng bộ từ engine mỗi khi danh sách thay đổi; [snapshot] luôn đọc trực tiếp.
 *
 * [persist] chụp snapshot trên thread gọi (main) rồi gọi [TabSnapshotStore.write] — store có I/O đĩa nên
 * caller nên dùng `snapshot()` trên main và ghi trên `Dispatchers.IO`; constructor cũng gọi [TabSnapshotStore.read].
 */
class TabManager(
    private val sessionFactory: EngineSessionFactory,
    private val store: TabSnapshotStore,
    val maxLiveSessions: Int = DEFAULT_MAX_LIVE_SESSIONS,
) {
    private class Entry(
        var tab: Tab,
        var session: EngineSession? = null,
        /** Dữ liệu `saveState()` khi tab không có session sống (discard/khôi phục). */
        var savedState: ByteArray? = null,
        var lastUsed: Long = 0,
    ) {
        val id: String get() = tab.id

        /** Đồng bộ url/title từ engine (bỏ qua giá trị rỗng của session chưa tải gì, vd. tab home). */
        fun syncFromSession(): Tab {
            val st = session?.state?.value ?: return tab
            tab =
                tab.copy(
                    url = st.url.ifEmpty { tab.url },
                    title = st.title.ifEmpty { tab.title },
                )
            return tab
        }
    }

    private class ClosedTab(
        val tab: Tab,
        val index: Int,
        val state: ByteArray?,
    )

    private val entries = mutableListOf<Entry>()
    private val closed = ArrayDeque<ClosedTab>()
    private var useCounter = 0L

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs

    private val _selectedTabId = MutableStateFlow<String?>(null)
    val selectedTabId: StateFlow<String?> = _selectedTabId

    init {
        require(maxLiveSessions >= 1) { "maxLiveSessions must be >= 1, was $maxLiveSessions" }
        restore(readStore())
        if (entries.isEmpty()) {
            val home = newEntry(HOME_URL, isPrivate = false)
            entries += home
            activate(home)
        }
        publish()
    }

    fun openTab(
        url: String,
        select: Boolean = true,
        isPrivate: Boolean = false,
    ): Tab {
        val entry = newEntry(url, isPrivate)
        entries += entry
        if (select) activate(entry) else enforceLiveLimit()
        publish()
        return entry.tab
    }

    fun selectTab(id: String) {
        val entry = find(id) ?: return
        activate(entry)
        publish()
    }

    fun closeTab(id: String) {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return
        val entry = entries.removeAt(index)
        val session = entry.session
        val state = session?.saveState() ?: entry.savedState
        closed.addLast(ClosedTab(entry.syncFromSession(), index, state))
        while (closed.size > MAX_UNDO) closed.removeFirst()
        session?.close()

        when {
            entries.isEmpty() -> activate(newEntry(HOME_URL, isPrivate = false).also { entries += it })
            id == _selectedTabId.value -> activate(entries[index.coerceAtMost(entries.lastIndex)])
        }
        publish()
    }

    /** Mở lại tab đóng gần nhất (tối đa [MAX_UNDO] tab), về đúng vị trí cũ, cùng id, và chọn nó. */
    fun undoClose(): Tab? {
        val c = closed.removeLastOrNull() ?: return null
        val tab = if (find(c.tab.id) == null) c.tab else c.tab.copy(id = newId())
        val entry = Entry(tab, savedState = c.state)
        entries.add(c.index.coerceIn(0, entries.size), entry)
        activate(entry)
        publish()
        return entry.tab
    }

    /** Session sống của tab, hoặc null nếu tab đã bị discard / không tồn tại. */
    fun session(id: String): EngineSession? = find(id)?.session

    /** Ảnh chụp hiện tại (bỏ tab ẩn danh). */
    fun snapshot(): TabsSnapshot {
        val persisted = entries.filterNot { it.tab.isPrivate }
        val selected =
            persisted.firstOrNull { it.id == _selectedTabId.value }
                ?: persisted.maxByOrNull { it.lastUsed }
        return TabsSnapshot(
            tabs =
                persisted.map { e ->
                    val tab = e.syncFromSession()
                    TabSnapshot(tab.id, tab.url, tab.title, e.session?.saveState() ?: e.savedState)
                },
            selectedTabId = selected?.id,
        )
    }

    /**
     * Giải phóng mọi session sống (giữ engine state trong snapshot) và xóa ngăn xếp undo.
     * Gọi khi màn hình/Activity bị hủy hẳn; sau đó [selectTab] sẽ tạo lại session nếu cần.
     */
    fun close() {
        entries.forEach(::discard)
        closed.clear()
        publish()
    }

    /** Ghi [snapshot] xuống [store] (I/O đĩa trên thread gọi). */
    fun persist() {
        store.write(snapshot())
    }

    private fun readStore(): TabsSnapshot? =
        try {
            store.read()
        } catch (_: RuntimeException) {
            null
        }

    private fun restore(snapshot: TabsSnapshot?) {
        if (snapshot == null) return
        val seen = mutableSetOf<String>()
        for (s in snapshot.tabs) {
            val id = if (seen.add(s.id)) s.id else newId().also { seen += it }
            entries += Entry(Tab(id, s.url, s.title), savedState = s.engineState)
        }
        val selected = entries.firstOrNull { it.id == snapshot.selectedTabId } ?: entries.firstOrNull()
        selected?.let(::activate)
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun newEntry(
        url: String,
        isPrivate: Boolean,
    ): Entry {
        val entry = Entry(Tab(newId(), url, isPrivate = isPrivate), lastUsed = ++useCounter)
        entry.session = sessionFactory.create().also { if (url != HOME_URL) it.loadUrl(url) }
        return entry
    }

    private fun find(id: String): Entry? = entries.firstOrNull { it.id == id }

    /** Chọn [entry], tạo lại session nếu tab đã bị discard, rồi áp giới hạn session sống. */
    private fun activate(entry: Entry) {
        if (entry.session == null) revive(entry)
        entry.lastUsed = ++useCounter
        _selectedTabId.value = entry.id
        enforceLiveLimit()
    }

    private fun revive(entry: Entry) {
        val session = sessionFactory.create()
        val restored = entry.savedState?.let(session::restoreState) ?: false
        // Chỉ tải lại URL web; HOME_URL hoặc URL lạ (file:, javascript:, ...) từ snapshot thì để trống.
        if (!restored && isValidWebUrl(entry.tab.url)) session.loadUrl(entry.tab.url)
        entry.session = session
        entry.savedState = null
    }

    private fun enforceLiveLimit() {
        while (entries.count { it.session != null } > maxLiveSessions) {
            val victim =
                entries
                    .filter { it.session != null && it.id != _selectedTabId.value }
                    .minByOrNull { it.lastUsed } ?: return
            discard(victim)
        }
    }

    private fun discard(entry: Entry) {
        val session = entry.session ?: return
        entry.syncFromSession()
        entry.savedState = session.saveState() ?: entry.savedState
        session.close()
        entry.session = null
    }

    private fun publish() {
        _tabs.value = entries.map { it.syncFromSession() }
    }

    companion object {
        const val DEFAULT_MAX_LIVE_SESSIONS = 6
        const val MAX_UNDO = 10
    }
}
