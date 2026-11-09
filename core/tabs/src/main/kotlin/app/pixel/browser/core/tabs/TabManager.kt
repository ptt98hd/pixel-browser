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
 * Trang chủ (spec 010) là trạng thái của tab ([Tab.isHome], `url == HOME_URL`), không phải trang trong engine:
 * [showHome] đè home lên trang hiện tại (session giữ nguyên), [loadUrl] rời home, [navigateBack] lùi theo thứ tự
 * rời home → `goBack()` → về home (nếu `backToHome`) → không làm gì. Khi ở home, url/title không đồng bộ từ engine.
 *
 * [persist] chụp snapshot trên thread gọi (main) rồi gọi [TabSnapshotStore.write] — store có I/O đĩa nên
 * caller nên dùng `snapshot()` trên main và ghi trên `Dispatchers.IO`; constructor cũng gọi [TabSnapshotStore.read].
 */
class TabManager(
    private val sessionFactory: EngineSessionFactory,
    private val store: TabSnapshotStore,
    val maxLiveSessions: Int = DEFAULT_MAX_LIVE_SESSIONS,
) {
    /** Trang nằm dưới home (spec 010): Back từ home quay lại trang này mà không reload. */
    private class PageUnderHome(
        val url: String,
        val title: String,
    )

    private class Entry(
        var tab: Tab,
        var session: EngineSession? = null,
        /** Dữ liệu `saveState()` khi tab không có session sống (discard/khôi phục). */
        var savedState: ByteArray? = null,
        var lastUsed: Long = 0,
        /** Khác null khi tab ở home đè lên một trang (spec 010). */
        var pageUnderHome: PageUnderHome? = null,
    ) {
        val id: String get() = tab.id

        /**
         * Đồng bộ url/title từ engine (bỏ qua giá trị rỗng của session chưa tải gì). Tab ở home đè lên một trang
         * (hoặc đã từng rời home) giữ nguyên `HOME_URL` và title rỗng: trang bên dưới tự điều hướng cũng không kéo
         * tab ra khỏi home.
         */
        fun syncFromSession(): Tab {
            if (tab.isHome) {
                // Home chưa dùng (không có trang bên dưới, chưa từng rời home) mà engine tự điều hướng (vd. request
                // của trang/popup, spec 007): không còn gì để "đè lên", tab trở thành tab trang web bình thường.
                val adopted =
                    session
                        ?.state
                        ?.value
                        ?.url
                        .orEmpty()
                if (pageUnderHome != null || tab.backToHome || adopted.isEmpty()) return tab
                tab = tab.copy(url = adopted, title = "")
            }
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
        val pageUnderHome: PageUnderHome?,
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

    /**
     * Tab cho popup của [openerId] (spec 007 AC12): chèn ngay sau opener, chọn nó; session mới không `loadUrl`
     * (engine của popup tự điều hướng). null nếu opener không tồn tại.
     */
    fun openTabForWindow(openerId: String): Tab? {
        val index = entries.indexOfFirst { it.id == openerId }
        if (index < 0) return null
        val opener = entries[index]
        // Không phải tab home (spec 010): engine của popup tự điều hướng, UI phải hiển thị engine chứ không phải home.
        val entry = newEntry(POPUP_URL, isPrivate = opener.tab.isPrivate, load = false)
        entries.add(index + 1, entry)
        activate(entry)
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
        val state = entry.persistableState()
        closed.addLast(ClosedTab(entry.syncFromSession(), index, state, entry.pageUnderHome))
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
        val entry = Entry(tab, savedState = c.state, pageUnderHome = c.pageUnderHome)
        entries.add(c.index.coerceIn(0, entries.size), entry)
        activate(entry)
        publish()
        return entry.tab
    }

    /**
     * Spec 010: home đè lên trang hiện tại của tab [id] (`stopLoading`, không đóng session, giữ lịch sử engine).
     * No-op nếu tab đã ở home hoặc id lạ.
     */
    fun showHome(id: String) {
        val entry = find(id) ?: return
        if (entry.tab.isHome) return
        val page = entry.syncFromSession()
        entry.session?.stopLoading()
        enterHome(entry, PageUnderHome(page.url, page.title))
        publish()
    }

    /**
     * Spec 010: tải [url] trong tab [id]; nếu tab đang ở home thì rời home và đặt `backToHome = true`.
     * Tab đã bị discard được tạo lại session (khôi phục lịch sử) trước khi tải. Id lạ → no-op.
     */
    fun loadUrl(
        id: String,
        url: String,
    ) {
        val entry = find(id) ?: return
        val revived = entry.session == null
        val session = entry.session ?: revive(entry, loadCurrent = false)
        if (entry.tab.isHome) {
            entry.pageUnderHome = null
            entry.tab = entry.tab.copy(url = url, title = "", backToHome = true)
        }
        session.loadUrl(url)
        if (revived) {
            entry.lastUsed = ++useCounter
            enforceLiveLimit()
        }
        publish()
    }

    /** Spec 010: tab [id] có gì để lùi không (rời home về trang bên dưới, `canGoBack`, hoặc `backToHome`). */
    fun canNavigateBack(id: String): Boolean {
        val entry = find(id) ?: return false
        if (entry.tab.isHome) return entry.pageUnderHome != null
        return entry.session
            ?.state
            ?.value
            ?.canGoBack == true || entry.tab.backToHome
    }

    /**
     * Spec 010: lùi một bước theo thứ tự (1) ở home đè lên trang → rời home (không reload, không `goBack`);
     * (2) engine `canGoBack` → `goBack()`; (3) không ở home và `backToHome` → về home "không có trang phía sau";
     * (4) trả false (caller để hệ thống xử lý). Không có chuỗi Back nào lặp lại trạng thái.
     *
     * Giả định tab đang chọn luôn có session ([selectTab] tạo lại session cho tab bị discard). Với tab không có
     * session (bị discard, chưa chọn lại) bước (2) bị bỏ qua và Back sẽ đi thẳng bước (3)/(4); caller chỉ nên gọi
     * cho tab đang chọn.
     */
    fun navigateBack(id: String): Boolean {
        val entry = find(id) ?: return false
        when {
            entry.tab.isHome -> {
                if (!leaveHome(entry)) return false
            }

            entry.session
                ?.state
                ?.value
                ?.canGoBack == true -> {
                entry.session?.goBack()
            }

            entry.tab.backToHome -> {
                entry.syncFromSession()
                entry.session?.stopLoading()
                enterHome(entry, page = null)
            }

            else -> {
                return false
            }
        }
        publish()
        return true
    }

    /**
     * Đồng bộ lại [tabs] từ trạng thái engine hiện tại (url/title, và việc tab home chưa dùng được engine điều hướng
     * "nhận" thành tab trang web). Không phát gì nếu danh sách không đổi.
     */
    fun refresh() = publish()

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
                    TabSnapshot(tab.id, tab.url, tab.title, e.persistableState(), tab.backToHome)
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
            // Migration (spec 010): tab home kiểu cũ là `about:blank` chưa có lịch sử.
            val isHome = s.url == HOME_URL || (s.url == LEGACY_HOME_URL && s.engineState == null)
            entries +=
                if (isHome) {
                    Entry(
                        Tab(id, HOME_URL, backToHome = s.backToHome),
                        savedState = s.engineState,
                        // Chỉ là dấu "có trang bên dưới": url/title thật được `leaveHome` đồng bộ từ engine sau
                        // `restoreState` (snapshot chỉ lưu HOME_URL cho tab đang ở home).
                        pageUnderHome = s.engineState?.let { PageUnderHome("", "") },
                    )
                } else {
                    Entry(Tab(id, s.url, s.title, backToHome = s.backToHome), savedState = s.engineState)
                }
        }
        val selected = entries.firstOrNull { it.id == snapshot.selectedTabId } ?: entries.firstOrNull()
        selected?.let(::activate)
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun newEntry(
        url: String,
        isPrivate: Boolean,
        load: Boolean = url != HOME_URL,
    ): Entry {
        val entry = Entry(Tab(newId(), url, isPrivate = isPrivate), lastUsed = ++useCounter)
        entry.session = sessionFactory.create().also { if (load) it.loadUrl(url) }
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

    /**
     * Tạo lại session cho tab đã bị discard: `restoreState` nếu có, ngược lại tải lại URL web của tab nếu
     * [loadCurrent]. Tab home không bao giờ `loadUrl`; khôi phục thất bại thì bỏ trang bên dưới home.
     */
    private fun revive(
        entry: Entry,
        loadCurrent: Boolean = true,
    ): EngineSession {
        val session = sessionFactory.create()
        val restored = entry.savedState?.let(session::restoreState) ?: false
        // Chỉ tải lại URL web; HOME_URL hoặc URL lạ (file:, javascript:, ...) từ snapshot thì để trống.
        if (!restored && loadCurrent && isValidWebUrl(entry.tab.url)) session.loadUrl(entry.tab.url)
        if (!restored && entry.tab.isHome) entry.pageUnderHome = null
        entry.session = session
        entry.savedState = null
        return session
    }

    /** Đưa tab vào home; [page] là trang bên dưới (Back quay lại), null = không có gì phía sau. */
    private fun enterHome(
        entry: Entry,
        page: PageUnderHome?,
    ) {
        entry.pageUnderHome = page
        entry.tab = entry.tab.copy(url = HOME_URL, title = "")
    }

    /** Rời home về trang bên dưới; false nếu không có trang nào. */
    private fun leaveHome(entry: Entry): Boolean {
        val page = entry.pageUnderHome ?: return false
        if (entry.session == null) {
            revive(entry, loadCurrent = false)
            entry.lastUsed = ++useCounter
            enforceLiveLimit()
        }
        // revive thất bại khôi phục → không còn trang bên dưới.
        if (entry.pageUnderHome == null) return false
        entry.pageUnderHome = null
        entry.tab = entry.tab.copy(url = page.url, title = page.title)
        entry.syncFromSession()
        return true
    }

    /**
     * Engine state để lưu/undo. Tab ở home không có trang bên dưới thì bỏ lịch sử engine: Back không bao giờ
     * tới được nó, và khôi phục sau này sẽ coi state khác null là "có trang bên dưới" (spec 010 AC6).
     */
    private fun Entry.persistableState(): ByteArray? =
        if (tab.isHome && pageUnderHome == null) null else session?.saveState() ?: savedState

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
        entry.savedState = entry.persistableState()
        session.close()
        entry.session = null
    }

    private fun publish() {
        _tabs.value = entries.map { it.syncFromSession() }
    }

    companion object {
        const val DEFAULT_MAX_LIVE_SESSIONS = 6
        const val MAX_UNDO = 10

        /** `HOME_URL` trước spec 010; snapshot cũ có thể còn giá trị này (migration AC6). */
        private const val LEGACY_HOME_URL = "about:blank"

        /** URL tạm của tab popup trước khi engine của nó điều hướng (spec 007 + 010). */
        private const val POPUP_URL = "about:blank"
    }
}
