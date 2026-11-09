package app.pixel.browser.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkFolder
import app.pixel.browser.core.bookmarks.BookmarkLimits
import app.pixel.browser.core.bookmarks.BookmarkNode
import app.pixel.browser.core.bookmarks.BookmarkRepository
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.DeletedTree
import app.pixel.browser.core.bookmarks.FolderOption
import app.pixel.browser.core.bookmarks.ImportedFolder
import app.pixel.browser.core.bookmarks.NetscapeBookmarks
import app.pixel.browser.core.bookmarks.NetscapeParse
import app.pixel.browser.core.bookmarks.ROOT_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Kết quả tìm kiếm phẳng kèm tên thư mục cha. */
data class BookmarkHit(
    val bookmark: Bookmark,
    val folderTitle: String,
)

/** Thông báo một lần của màn hình Dấu trang; UI ánh xạ sang string resource. */
sealed interface BookmarksMessage {
    data class ImportDone(
        val imported: Int,
        val skipped: Int,
    ) : BookmarksMessage

    data object ImportTooLarge : BookmarksMessage

    data object ImportUnreadable : BookmarksMessage

    data object ImportNotBookmarkFile : BookmarksMessage

    data object ImportFull : BookmarksMessage

    data class ExportDone(
        val count: Int,
    ) : BookmarksMessage

    data object ExportFailed : BookmarksMessage

    /** Thao tác thư mục/di chuyển/tạo thất bại (vd. `Cycle`, `TooDeep`, `NotFound`, `Full`). */
    data object OperationFailed : BookmarksMessage
}

data class BookmarksUiState(
    val folderId: Long = ROOT_ID,
    /** Tên thư mục đang xem (gốc: "Dấu trang"). */
    val title: String = "",
    val children: List<BookmarkNode> = emptyList(),
    /** Số mục con của từng thư mục trong [children]. */
    val childCounts: Map<Long, Int> = emptyMap(),
    val query: String = "",
    /** `null` khi không ở chế độ tìm; ngược lại kết quả phẳng (có thể rỗng). */
    val results: List<BookmarkHit>? = null,
    val pendingUndo: DeletedTree? = null,
    val message: BookmarksMessage? = null,
    /** Tên file gợi ý cho xuất: `pixel-bookmarks-<yyyyMMdd>.html`. */
    val exportFileName: String = "pixel-bookmarks.html",
)

class BookmarksViewModel(
    private val repo: BookmarkRepository,
    private val files: BookmarkFileGateway,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Đọc/parse/ghi file chạy trên dispatcher này (test tiêm dispatcher kiểm soát được). */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Tên hiển thị của gốc (kho lưu "Dấu trang" cố định); `:app` truyền chuỗi theo locale. */
    private val rootTitle: String = DEFAULT_ROOT_TITLE,
    /** Tiền tố tên thư mục nhập: "<tiền tố> <yyyy-MM-dd>"; mặc định theo AC16. */
    private val importFolderPrefix: String = DEFAULT_IMPORT_PREFIX,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            BookmarksUiState(
                exportFileName = "pixel-bookmarks-${today().format(DateTimeFormatter.BASIC_ISO_DATE)}.html",
            ),
        )
    val state: StateFlow<BookmarksUiState> = _state.asStateFlow()

    private var folderId = ROOT_ID
    private var watchJob: Job? = null
    private var searchJob: Job? = null

    init {
        watch(ROOT_ID)
    }

    private fun today() = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    /**
     * Theo dõi thư mục [id]: nạp lại khi chính nó hoặc bất kỳ tổ tiên nào đổi (xóa một tổ tiên chỉ làm đổi danh
     * sách con của cha nó), và về tổ tiên gần nhất còn tồn tại nếu [id] đã bị xóa.
     */
    private fun watch(id: Long) {
        watchJob?.cancel()
        folderId = id
        _state.update { it.copy(folderId = id) }
        watchJob =
            viewModelScope.launch {
                val chain = ancestorsOf(id)
                val triggers = (listOf(id) + chain).map { repo.observeChildren(it) }
                merge(*triggers.toTypedArray()).collect { refresh(id, chain) }
            }
    }

    private suspend fun ancestorsOf(id: Long): List<Long> {
        val chain = ArrayList<Long>()
        var cur = if (id == ROOT_ID) null else guarded { repo.folder(id)?.parentId }
        while (cur != null && chain.size <= BookmarkLimits.MAX_DEPTH + 1) {
            val folderId = cur
            chain += folderId
            cur = guarded { repo.folder(folderId)?.parentId }
        }
        return chain
    }

    private suspend fun refresh(
        id: Long,
        chain: List<Long>,
    ) {
        val folder = guarded { repo.folder(id) }
        if (folder == null) {
            watch(chain.firstOrNull { guarded { repo.folder(it) } != null } ?: ROOT_ID)
            return
        }
        val children = guarded { repo.observeChildren(id).first() } ?: return
        val all = guarded { repo.childCounts() }.orEmpty()
        val counts = children.filterIsInstance<BookmarkFolder>().associate { it.id to (all[it.id] ?: 0) }
        _state.update {
            it.copy(
                folderId = id,
                title = displayTitle(folder.id, folder.title),
                children = children,
                childCounts = counts,
            )
        }
    }

    fun openFolder(id: Long) {
        if (id != folderId) watch(id)
    }

    fun navigateUp(): Boolean {
        val current = folderId
        if (current == ROOT_ID) return false
        viewModelScope.launch {
            val parent = guarded { repo.folder(current)?.parentId } ?: ROOT_ID
            if (folderId == current) watch(parent)
        }
        return true
    }

    fun onQueryChange(q: String) {
        searchJob?.cancel()
        _state.update { it.copy(query = q, results = if (q.isBlank()) null else it.results) }
        if (q.isBlank()) return
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                runSearch(q)
            }
    }

    private suspend fun runSearch(q: String) {
        val titles = HashMap<Long, String>()
        val hits =
            guarded { repo.search(q, MAX_RESULTS) }
                ?.map { b ->
                    val parent = b.parentId
                    val title =
                        if (parent == null) {
                            ""
                        } else {
                            titles.getOrPut(parent) {
                                guarded { repo.folder(parent) }?.let { displayTitle(it.id, it.title) }.orEmpty()
                            }
                        }
                    BookmarkHit(b, title)
                } ?: return
        if (_state.value.query == q) _state.update { it.copy(results = hits) }
    }

    private fun refreshSearch() {
        val q = _state.value.query
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(q) }
    }

    fun createFolder(title: String) {
        val parent = folderId
        viewModelScope.launch { report(guarded { repo.createFolder(parent, title) }) }
    }

    fun rename(
        id: Long,
        title: String,
    ) {
        viewModelScope.launch {
            report(guarded { repo.rename(id, title) })
            refreshSearch()
        }
    }

    fun move(
        id: Long,
        parentId: Long,
    ) {
        viewModelScope.launch {
            report(guarded { repo.move(id, parentId) })
            refreshSearch()
        }
    }

    /** Spec ghi non-suspend; bản này `suspend` vì repository là suspend. */
    suspend fun edit(
        id: Long,
        title: String,
        url: String,
        parentId: Long,
    ): BookmarkResult<Bookmark> {
        val result = guarded { repo.updateBookmark(id, title, url, parentId) } ?: return BookmarkResult.NotFound
        if (result is BookmarkResult.Ok) guarded { repo.setLastFolderId(parentId) }
        refreshSearch()
        return result
    }

    suspend fun folderOptions(excludeSubtreeOf: Long?): List<FolderOption> =
        guarded {
            repo.folderOptions(
                excludeSubtreeOf,
            )
        }.orEmpty().map { it.copy(title = displayTitle(it.id, it.title)) }

    private fun displayTitle(
        id: Long,
        stored: String,
    ) = if (id == ROOT_ID) rootTitle else stored

    fun delete(id: Long) {
        viewModelScope.launch {
            when (val result = guarded { repo.delete(id) }) {
                is BookmarkResult.Ok -> _state.update { it.copy(pendingUndo = result.value) }
                else -> report(result)
            }
            refreshSearch()
        }
    }

    fun undoDelete() {
        val tree = _state.value.pendingUndo ?: return
        _state.update { it.copy(pendingUndo = null) }
        viewModelScope.launch {
            if (guarded { repo.restore(tree) } != true) setMessage(BookmarksMessage.OperationFailed)
            refreshSearch()
        }
    }

    /** Snackbar hoàn tác tự tắt → bỏ [BookmarksUiState.pendingUndo]. */
    fun onUndoExpired() {
        _state.update { it.copy(pendingUndo = null) }
    }

    fun import(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val read = guarded { withContext(ioDispatcher) { files.read(uri, BookmarkLimits.MAX_IMPORT_BYTES + 1) } }
            val message =
                when (read) {
                    null, FileRead.Unreadable -> BookmarksMessage.ImportUnreadable
                    FileRead.TooLarge -> BookmarksMessage.ImportTooLarge
                    is FileRead.Bytes -> importBytes(read.bytes)
                }
            setMessage(message)
        }
    }

    private suspend fun importBytes(bytes: ByteArray): BookmarksMessage =
        when (val parsed = withContext(ioDispatcher) { NetscapeBookmarks.parse(bytes) }) {
            NetscapeParse.TooLarge -> {
                BookmarksMessage.ImportTooLarge
            }

            NetscapeParse.NotBookmarkFile -> {
                BookmarksMessage.ImportNotBookmarkFile
            }

            is NetscapeParse.Parsed -> {
                when (val result = guarded { repo.importTree(parsed.root, "$importFolderPrefix ${today()}") }) {
                    is BookmarkResult.Ok -> BookmarksMessage.ImportDone(result.value, parsed.skipped)
                    BookmarkResult.Full -> BookmarksMessage.ImportFull
                    else -> BookmarksMessage.OperationFailed
                }
            }
        }

    fun export(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val tree = guarded { repo.exportTree() }
            val written =
                tree != null &&
                    guarded {
                        withContext(
                            ioDispatcher,
                        ) { files.write(uri, NetscapeBookmarks.write(tree.copy(title = rootTitle))) }
                    } == true
            setMessage(
                if (tree != null &&
                    written
                ) {
                    BookmarksMessage.ExportDone(countBookmarks(tree))
                } else {
                    BookmarksMessage.ExportFailed
                },
            )
        }
    }

    private fun countBookmarks(root: ImportedFolder): Int {
        var count = 0
        val pending = ArrayDeque(listOf(root))
        while (pending.isNotEmpty()) {
            val folder = pending.removeLast()
            count += folder.bookmarks.size
            pending.addAll(folder.folders)
        }
        return count
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun setMessage(message: BookmarksMessage) {
        _state.update { it.copy(message = message) }
    }

    /** Kết quả không phải `Ok` (hoặc lỗi lưu trữ → `null`) → thông báo thất bại. */
    private fun report(result: BookmarkResult<*>?) {
        if (result !is BookmarkResult.Ok) setMessage(BookmarksMessage.OperationFailed)
    }

    /** Chạy [block]; lỗi lưu trữ (trừ hủy coroutine) → `null` thay vì làm sập ViewModel. */
    private suspend fun <T> guarded(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MAX_RESULTS = 200
        const val DEFAULT_IMPORT_PREFIX = "Đã nhập"
        const val DEFAULT_ROOT_TITLE = "Dấu trang"
    }
}
