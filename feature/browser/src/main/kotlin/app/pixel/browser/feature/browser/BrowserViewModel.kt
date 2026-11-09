package app.pixel.browser.feature.browser

import android.Manifest
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkRepository
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.BookmarkRules
import app.pixel.browser.core.bookmarks.DeletedTree
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.SitePermission
import app.pixel.browser.core.engine.isSecureOrigin
import app.pixel.browser.core.engine.isValidWebUrl
import app.pixel.browser.core.engine.originOf
import app.pixel.browser.core.engine.schemeOf
import app.pixel.browser.core.history.PageVisit
import app.pixel.browser.core.history.PageVisitRecorder
import app.pixel.browser.core.omnibox.DefaultSuggestionProvider
import app.pixel.browser.core.omnibox.OmniboxClassifier
import app.pixel.browser.core.omnibox.OmniboxResult
import app.pixel.browser.core.omnibox.SearchEngine
import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.Suggestion
import app.pixel.browser.core.omnibox.SuggestionProvider
import app.pixel.browser.core.omnibox.UrlDisplay
import app.pixel.browser.core.tabs.HOME_URL
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.intent.ExternalAppLauncher
import app.pixel.browser.feature.browser.intent.ExternalLaunchResult
import app.pixel.browser.feature.browser.intent.IncomingRequest
import app.pixel.browser.feature.browser.tabs.TabThumbnailRepository
import app.pixel.browser.feature.browser.web.RuntimePermissionGate
import app.pixel.browser.feature.browser.web.SitePermissionMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Mã thông báo trong [BrowserUiState.message]; UI ánh xạ sang string resource. */
object BrowserMessages {
    /** Input omnibox bị chặn (scheme nguy hiểm/không hỗ trợ, spec 004). */
    const val BLOCKED_INPUT = "blocked_input"

    /** Intent từ app khác bị từ chối (spec 006 AC13). */
    const val INTENT_REJECTED = "intent_rejected"

    /** Không có app nào mở được link ngoài (spec 006 AC23). */
    const val EXTERNAL_APP_NOT_FOUND = "external_app_not_found"

    /** Link ngoài bị chặn vì lý do an toàn (spec 006 AC23). */
    const val EXTERNAL_APP_BLOCKED = "external_app_blocked"

    /** Không mở được màn hình cài đặt trình duyệt mặc định (spec 006). */
    const val SETTINGS_UNAVAILABLE = "settings_unavailable"

    /** Popup không có gesture đã bị chặn (spec 007 AC21). */
    const val POPUP_BLOCKED = "popup_blocked"

    /** Hệ thống từ chối (một phần) quyền mà trang xin; UI gợi ý mở cài đặt app (spec 007 AC15). */
    const val PERMISSION_DENIED = "permission_denied"

    /** Link tải không phải http/https (blob:, data:) – chưa hỗ trợ (spec 007 AC24). */
    const val DOWNLOAD_UNSUPPORTED = "download_unsupported"

    /** DownloadManager từ chối link tải (spec 007 AC24). */
    const val DOWNLOAD_FAILED = "download_failed"

    /** Không có app chọn file/chụp ảnh (spec 007 AC19). */
    const val FILE_CHOOSER_UNAVAILABLE = "file_chooser_unavailable"

    /** Đã thêm dấu trang; snackbar có nút "Sửa" (spec 013 AC18/AC19). */
    const val BOOKMARK_ADDED = "bookmark_added"

    /** Thêm dấu trang thất bại (đầy, lỗi lưu trữ; spec 013 AC18). */
    const val BOOKMARK_FAILED = "bookmark_failed"

    /** Đã xóa dấu trang từ hộp thoại sửa; snackbar có nút "Hoàn tác" (spec 013 AC20). */
    const val BOOKMARK_REMOVED = "bookmark_removed"
}

/**
 * State holder của màn hình trình duyệt (spec 005). Kết hợp [TabManager] với `state` của session được chọn.
 * Tab ở trang chủ native (spec 010) là trạng thái của [TabManager]
 * (`Tab.isHome`): ViewModel không tải URL nào cho home; điều hướng đi qua [TabManager.loadUrl]/[TabManager.navigateBack].
 *
 * Phải dùng trên main thread (như [TabManager]). [uiState] được tính lại đồng bộ từ giá trị hiện tại của
 * [TabManager] và các session mỗi khi một trong các flow đó phát, hoặc sau mỗi hành động.
 */
class BrowserViewModel(
    private val tabManager: TabManager,
    private val searchEngines: StateFlow<SearchEngine> = MutableStateFlow(SearchEngines.Default),
    private val suggestionProvider: SuggestionProvider = DefaultSuggestionProvider { searchEngines.value },
    private val externalAppLauncher: ExternalAppLauncher = ExternalAppLauncher { ExternalLaunchResult.Rejected },
    /** Đồng hồ đơn điệu (ms) cho throttle mở app ngoài. */
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    /** Lựa chọn quyền theo site trong process, dùng chung giữa các tab (spec 007 AC16). */
    private val permissionMemory: SitePermissionMemory = SitePermissionMemory(),
    /** Xin quyền Android lúc chạy (spec 007 AC15); UI dùng chung cho chụp ảnh (AC19). */
    val runtimePermissions: RuntimePermissionGate = RuntimePermissionGate { emptySet() },
    /** Thumbnail của tab cho tab switcher (spec 009). */
    private val thumbnails: TabThumbnailRepository = TabThumbnailRepository.None,
    /** Ghi lịch sử khi trang tải xong (spec 012). */
    private val pageVisits: PageVisitRecorder = PageVisitRecorder.None,
    /** Kho dấu trang (spec 013); null → không có tính năng dấu trang. */
    private val bookmarks: BookmarkRepository? = null,
) : ViewModel() {
    private val _bookmarkEditRequests = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /** Yêu cầu mở hộp thoại sửa dấu trang (id) khi người dùng chọn "Sửa dấu trang" (spec 013 AC18). */
    val bookmarkEditRequests: SharedFlow<Long> = _bookmarkEditRequests.asSharedFlow()

    /** Dấu trang của trang hiện tại (so theo `urlKey`), cập nhật từ [BookmarkRepository.observeByUrl]. */
    private var currentBookmark: Bookmark? = null
    private var bookmarkKey: String? = null
    private var bookmarkJob: Job? = null
    private var addingBookmark = false

    /** Cây vừa xóa từ hộp thoại sửa, để "Hoàn tác" trên snackbar (chỉ giữ cái gần nhất). */
    private var removedBookmark: DeletedTree? = null

    /** Thêm dấu trang cho trang hiện tại; đã có thì phát [bookmarkEditRequests] (spec 013 AC18). */
    fun onToggleBookmark() {
        val repo = bookmarks ?: return
        val state = _uiState.value
        if (!state.canBookmark) return
        currentBookmark?.let { existing ->
            _bookmarkEditRequests.tryEmit(existing.id)
            return
        }
        if (addingBookmark) return
        addingBookmark = true
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val added =
                try {
                    val folder = repo.lastFolderId()
                    // Chạm hai lần nhanh / nơi khác vừa lưu: kiểm lại trước khi thêm để không tạo bản trùng.
                    val existing = repo.observeByUrl(state.url).first()
                    if (existing != null) {
                        currentBookmark = existing
                        _bookmarkEditRequests.tryEmit(existing.id)
                        recompute()
                        return@launch
                    }
                    repo.addBookmark(state.url, state.title, folder) as? BookmarkResult.Ok
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } finally {
                    addingBookmark = false
                }
            message = if (added != null) BrowserMessages.BOOKMARK_ADDED else BrowserMessages.BOOKMARK_FAILED
            if (added != null && bookmarkKey == BookmarkRules.urlKey(added.value.url)) currentBookmark = added.value
            recompute()
        }
    }

    /** Một dấu trang vừa bị xóa từ hộp thoại sửa: hiện snackbar "Đã xóa" có "Hoàn tác" (spec 013 AC20). */
    fun onBookmarkRemoved(tree: DeletedTree) {
        removedBookmark = tree
        message = BrowserMessages.BOOKMARK_REMOVED
        recompute()
    }

    /** Hoàn tác lần xóa dấu trang gần nhất (nếu còn khôi phục được). */
    fun onUndoBookmarkRemoved() {
        val tree = removedBookmark ?: return
        removedBookmark = null
        val repo = bookmarks ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val restored =
                try {
                    repo.restore(tree)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            if (!restored) {
                message = BrowserMessages.BOOKMARK_FAILED
                recompute()
            }
        }
    }

    /** Theo dõi dấu trang của [url] (chỉ khi có thể lưu); đổi `urlKey` mới khởi động lại collector. */
    private fun trackBookmark(url: String?) {
        val repo = bookmarks
        val key = if (repo != null && url != null) BookmarkRules.urlKey(url) else null
        if (key == bookmarkKey) return
        bookmarkKey = key
        bookmarkJob?.cancel()
        bookmarkJob = null
        currentBookmark = null
        if (repo == null || url == null) return
        bookmarkJob =
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    repo.observeByUrl(url).collect {
                        currentBookmark = it
                        recompute()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    currentBookmark = null
                }
            }
    }

    /** Engine hiện tại (đọc mỗi lần dùng: onSubmit, intent, gợi ý). */
    val searchEngine: SearchEngine get() = searchEngines.value

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _selectedSession = MutableStateFlow<EngineSession?>(null)

    /** Session của tab đang chọn (null nếu tạm thời không có). */
    val selectedSession: StateFlow<EngineSession?> = _selectedSession.asStateFlow()

    private var isEditing = false
    private var suggestions: List<Suggestion> = emptyList()
    private var message: String? = null
    private var suggestionJob: Job? = null
    private var sessionsJob: Job? = null
    private var observedSessions: List<EngineSession>? = null

    /** Tab mở từ intent ngoài (spec 006 AC14), chỉ trong bộ nhớ. */
    private val externalTabIds = mutableSetOf<String>()

    /** Trạng thái xử lý request `ExternalApp` của từng session sống (spec 006 AC23–AC25). */
    private class ExternalAppState {
        /** Id request lớn nhất đã xử lý. */
        var lastSeen = 0L

        /** URL trang lúc người dùng hủy hộp thoại; khác null → bỏ request không gesture cho tới trang mới. */
        var suppressedAtUrl: String? = null

        /** Thời điểm (theo [clock]) lần mở thẳng gần nhất. */
        var lastDirectLaunch: Long? = null
    }

    private val externalApps = HashMap<EngineSession, ExternalAppState>()
    private val visitJobs = HashMap<EngineSession, Job>()

    /**
     * Kế hoạch cho một `PermissionPrompt` (spec 007 AC16): [ask] là quyền chưa nhớ (hiện trong dialog),
     * [remembered] là quyền đã nhớ Allow. [inFlight] = đang chờ hệ thống cấp quyền, không hiện dialog.
     */
    private class PermissionPlan(
        val ask: Set<SitePermission>,
        val remembered: Set<SitePermission>,
        /** Origin xin = origin trang chính; iframe khác origin không dùng/ghi bộ nhớ quyền. */
        val rememberable: Boolean,
        var inFlight: Boolean = false,
    )

    private val permissionPlans = HashMap<EngineSession, HashMap<Long, PermissionPlan>>()

    /** Tab popup (spec 007 AC21) → tab đã mở nó; dùng khi popup gọi `window.close()` (AC22). */
    private val popupOpeners = HashMap<String, String>()
    private var isAppVisible = true
    private var showSetDefaultBrowser = false
    private var recomputing = false
    private var recomputePending = false

    init {
        thumbnails.retainOnly(
            tabManager.tabs.value
                .filterNot { it.isPrivate }
                .map { it.id }
                .toSet(),
        )
        recompute()
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            merge(tabManager.tabs, tabManager.selectedTabId).collect { recompute() }
        }
    }

    /** Lùi theo [TabManager.navigateBack]: rời home, `goBack()` của engine, hoặc về home nếu tab rời home (spec 010). */
    fun onBack() {
        val id = tabManager.selectedTabId.value ?: return
        if (tabManager.navigateBack(id)) recompute()
    }

    fun onForward() {
        val s = current() ?: return
        if (s.state.value.canGoForward) s.goForward()
    }

    /** Hiện trang chủ native đè lên trang hiện tại (spec 010); không làm gì nếu đã ở home. */
    fun onHome() {
        val id = tabManager.selectedTabId.value ?: return
        stopEditing()
        exitFullscreen(current())
        tabManager.showHome(id)
        recompute()
    }

    fun onReload() {
        current()?.reload()
        recompute()
    }

    fun onNewTab() {
        stopEditing()
        captureCurrentThumbnail()
        exitFullscreen(current())
        tabManager.openTab(HOME_URL)
        recompute()
    }

    fun onScrollToTop() {
        current()?.scrollToTop()
    }

    fun onScrollToBottom() {
        current()?.scrollToBottom()
    }

    fun onStartEditing() {
        isEditing = true
        suggestions = emptyList()
        recompute()
    }

    fun onCancelEditing() {
        stopEditing()
        recompute()
    }

    fun onQueryChange(text: String) {
        if (!isEditing) return
        suggestionJob?.cancel()
        suggestionJob =
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val result = suggestionProvider.suggest(text)
                if (isEditing) {
                    suggestions = result
                    recompute()
                }
            }
    }

    fun onSubmit(input: String) {
        when (val result = OmniboxClassifier.classify(input, searchEngine)) {
            OmniboxResult.Empty -> {
                return
            }

            is OmniboxResult.Blocked -> {
                message = BrowserMessages.BLOCKED_INPUT
            }

            is OmniboxResult.Navigate -> {
                navigate(result.url)
            }

            is OmniboxResult.Search -> {
                navigate(result.url)
            }
        }
        recompute()
    }

    /** Mở [url] (chỉ URL web hợp lệ, vd. shortcut trang chủ) trong tab đang chọn, rời home nếu cần (spec 010). */
    fun onOpenUrl(url: String) {
        if (!isValidWebUrl(url)) return
        navigate(url)
        recompute()
    }

    /** Mở [url] ở tab mới và chọn nó (chỉ URL web hợp lệ; spec 012). */
    fun onOpenUrlInNewTab(url: String) {
        if (!isValidWebUrl(url)) return
        stopEditing()
        captureCurrentThumbnail()
        exitFullscreen(current())
        tabManager.openTab(url)
        recompute()
    }

    fun onSuggestionClick(suggestion: Suggestion) {
        navigate(suggestion.url)
        recompute()
    }

    fun onSelectTab(id: String) {
        stopEditing()
        if (id != tabManager.selectedTabId.value) {
            captureCurrentThumbnail()
            exitFullscreen(current())
        }
        tabManager.selectTab(id)
        recompute()
    }

    /** Spec 008 AC13: đổi sang tab kề (-1 trước, +1 sau); no-op ngoài biên. */
    fun onSelectAdjacentTab(delta: Int) {
        val ids = tabManager.tabs.value.map { it.id }
        val index = ids.indexOf(tabManager.selectedTabId.value)
        if (index < 0) return
        onSelectTab(ids.getOrNull(index + delta) ?: return)
    }

    /** Spec 009 AC8: chụp thumbnail tab hiện tại trước khi mở switcher. */
    fun onOpenTabSwitcher() {
        stopEditing()
        captureCurrentThumbnail()
        recompute()
    }

    /** Spec 009 AC9: hoàn tác đóng tab gần nhất (lựa chọn tab theo [TabManager.undoClose]). */
    fun onUndoCloseTab() {
        tabManager.undoClose()
        recompute()
    }

    /** Spec 009 AC9: đóng tất cả tab, còn lại một tab home. */
    fun onCloseAllTabs() {
        stopEditing()
        exitFullscreen(current())
        val old = tabManager.tabs.value.map { it.id }
        tabManager.closeAllTabs()
        old.forEach {
            thumbnails.remove(it)
            pageVisits.forgetTab(it)
        }
        recompute()
    }

    /** Favicon của session tab [tabId] (spec 009); null nếu tab không có session sống. */
    fun faviconOf(tabId: String): StateFlow<Bitmap?>? = tabManager.session(tabId)?.favicon

    private fun captureCurrentThumbnail() {
        val id = tabManager.selectedTabId.value ?: return
        val tab = tabManager.tabs.value.firstOrNull { it.id == id } ?: return
        if (tab.isHome) return
        val session = tabManager.session(id) ?: return
        thumbnails.capture(id, session, persist = !tab.isPrivate)
    }

    fun onCloseTab(id: String) {
        exitFullscreen(tabManager.session(id))
        closeAndForget(id)
        thumbnails.remove(id)
        recompute()
    }

    fun onMessageShown() {
        message = null
        recompute()
    }

    /**
     * Intent từ app khác (spec 006 AC13): mở URL ở tab mới được chọn, hoặc trong tab home ban đầu nếu tab đó
     * chưa có lịch sử. Tab nhận URL được ghi nhận để Back ở gốc lịch sử quay về app gọi (AC14).
     */
    fun onIncomingIntent(request: IncomingRequest) {
        val url =
            when (request) {
                is IncomingRequest.OpenUrl -> request.url
                is IncomingRequest.Search -> request.url
                IncomingRequest.Resume -> return
                is IncomingRequest.Rejected -> null
            }
        stopEditing()
        exitFullscreen(current())
        if (url == null || !isValidWebUrl(url)) {
            message = BrowserMessages.INTENT_REJECTED
        } else {
            val reusable = unusedHomeTab()
            val id =
                if (reusable != null) {
                    tabManager.loadUrl(reusable, url)
                    reusable
                } else {
                    tabManager.openTab(url).id
                }
            externalTabIds += id
        }
        recompute()
    }

    /**
     * Back ở gốc lịch sử của tab mở từ intent (spec 006 AC14): đóng tab đó. Caller (Activity) sau đó đưa task
     * về sau để quay lại app gọi. Không làm gì nếu tab đang chọn không mở từ intent hoặc còn lịch sử.
     */
    fun onReturnToCaller() {
        val id = tabManager.selectedTabId.value ?: return
        if (id !in externalTabIds || current()?.state?.value?.canGoBack == true) return
        stopEditing()
        externalTabIds -= id
        closeAndForget(id)
        recompute()
    }

    /** Người dùng trả lời hộp thoại mở app ngoài của tab đang chọn (spec 006 AC24). */
    fun onExternalAppDecision(
        requestId: Long,
        open: Boolean,
    ) {
        val tabId = tabManager.selectedTabId.value ?: return
        onExternalAppDecision(tabId, requestId, open)
    }

    /**
     * Người dùng trả lời hộp thoại mở app ngoài cho request [requestId] của tab [tabId] (spec 006 AC24). Không
     * làm gì nếu tab đó không còn request này đang chờ. Hủy → các request không gesture tiếp theo của trang này
     * bị bỏ (`Dismiss(suppressFurther = true)`) cho tới khi trang đổi hoặc có request có gesture.
     */
    fun onExternalAppDecision(
        tabId: String,
        requestId: Long,
        open: Boolean,
    ) {
        val session = tabManager.session(tabId) ?: return
        val request =
            session.requests.value
                .filterIsInstance<EngineRequest.ExternalApp>()
                .firstOrNull { it.id == requestId } ?: return
        if (!session.respond(request.id, EngineResponse.Dismiss())) return
        if (open) {
            launchExternal(session, request)
        } else {
            externalApps.getOrPut(session, ::ExternalAppState).suppressedAtUrl = session.state.value.url
        }
        recompute()
    }

    /** Trả lời request [requestId] của tab đang chọn (spec 007). */
    fun onRespond(
        requestId: Long,
        response: EngineResponse,
    ) {
        val tabId = tabManager.selectedTabId.value ?: return
        onRespond(tabId, requestId, response)
    }

    /**
     * Trả lời request [requestId] của tab [tabId] (tab lúc dialog/picker được hiện). Không làm gì nếu tab đó không
     * còn request này đang chờ – kết quả đến muộn không bao giờ rơi sang tab khác.
     */
    fun onRespond(
        tabId: String,
        requestId: Long,
        response: EngineResponse,
    ) {
        val session = tabManager.session(tabId) ?: return
        if (session.requests.value.none { it.id == requestId }) return
        session.respond(requestId, response)
        recompute()
    }

    /**
     * Người dùng Allow/Block hộp thoại quyền của tab đang chọn (spec 007 AC15–AC16). Lựa chọn được nhớ cho các
     * quyền trong dialog; kết quả gộp với quyền đã nhớ Allow rồi xin hệ thống những quyền Android còn thiếu.
     */
    fun onPermissionDecision(
        requestId: Long,
        allow: Boolean,
    ) {
        val tabId = tabManager.selectedTabId.value ?: return
        onPermissionDecision(tabId, requestId, allow)
    }

    /** Như [onPermissionDecision] cho request của tab [tabId] (tab lúc dialog được hiện). */
    fun onPermissionDecision(
        tabId: String,
        requestId: Long,
        allow: Boolean,
    ) {
        val session = tabManager.session(tabId) ?: return
        val request =
            session.requests.value
                .filterIsInstance<EngineRequest.PermissionPrompt>()
                .firstOrNull { it.id == requestId } ?: return
        val plan = permissionPlan(session, request)
        if (plan.inFlight) return
        if (plan.rememberable) plan.ask.forEach { permissionMemory.set(request.origin, it, allow) }
        grantPermissions(session, request, plan, if (allow) plan.remembered + plan.ask else plan.remembered)
        recompute()
    }

    /** Thoát video toàn màn hình của tab đang chọn (spec 007 AC20). */
    fun onExitFullscreen() {
        exitFullscreen(current())
        recompute()
    }

    /** DownloadManager không nhận link tải (spec 007 AC24). */
    fun onDownloadFailed() {
        message = BrowserMessages.DOWNLOAD_FAILED
        recompute()
    }

    /** Không mở được trình chọn file/camera (spec 007 AC19). */
    fun onFileChooserUnavailable() {
        message = BrowserMessages.FILE_CHOOSER_UNAVAILABLE
        recompute()
    }

    /** App hiển thị (≥ STARTED) hay không; khi ẩn, request có gesture đi qua hộp thoại thay vì mở thẳng. */
    fun onAppVisibilityChanged(visible: Boolean) {
        isAppVisible = visible
    }

    /** Không mở được Settings để đặt trình duyệt mặc định (spec 006, edge case ROM lạ). */
    fun onDefaultBrowserSettingsUnavailable() {
        message = BrowserMessages.SETTINGS_UNAVAILABLE
        recompute()
    }

    /** Trạng thái trình duyệt mặc định được tính lại (spec 006 AC18). */
    fun onDefaultBrowserChanged(isDefault: Boolean) {
        showSetDefaultBrowser = !isDefault
        recompute()
    }

    /** Ảnh chụp tab để caller ghi xuống đĩa ngoài main thread (spec 002). Gọi trên main thread. */
    fun tabsSnapshot(): TabsSnapshot = tabManager.snapshot()

    override fun onCleared() {
        tabManager.close()
    }

    private fun current(): EngineSession? = tabManager.selectedTabId.value?.let(tabManager::session)

    private fun exitFullscreen(session: EngineSession?) {
        if (session?.state?.value?.isFullscreen == true) session.exitFullscreen()
    }

    private fun tabIdOf(session: EngineSession): String? =
        tabManager.tabs.value
            .firstOrNull { tabManager.session(it.id) === session }
            ?.id

    /**
     * Xử lý các request của spec 007 mà ViewModel tự quyết (AC13): popup (AC21), `window.close()` (AC22), quyền
     * từ origin không an toàn (AC17) hoặc đã nhớ (AC16), link tải không phải http/https (AC24).
     */
    private fun processWebRequests() {
        for (session in observedSessions.orEmpty()) {
            // Bỏ kế hoạch quyền của request đã được trả lời/hủy.
            permissionPlans[session]?.keys?.retainAll(
                session.requests.value
                    .map { it.id }
                    .toSet(),
            )
            for (request in session.requests.value) {
                when (request) {
                    is EngineRequest.NewWindow -> {
                        openPopup(session, request)
                    }

                    is EngineRequest.PopupBlocked -> {
                        session.respond(request.id, EngineResponse.Dismiss())
                        message = BrowserMessages.POPUP_BLOCKED
                    }

                    is EngineRequest.CloseWindow -> {
                        closeWindow(session, request)
                    }

                    is EngineRequest.PermissionPrompt -> {
                        autoDecidePermission(session, request)
                    }

                    is EngineRequest.Download -> {
                        if (schemeOf(request.url) !in DOWNLOAD_SCHEMES) {
                            session.respond(request.id, EngineResponse.Dismiss())
                            message = BrowserMessages.DOWNLOAD_UNSUPPORTED
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }

    private fun openPopup(
        opener: EngineSession,
        request: EngineRequest.NewWindow,
    ) {
        val openerId = tabIdOf(opener)
        if (openerId == null) {
            opener.respond(request.id, EngineResponse.Dismiss())
            return
        }
        exitFullscreen(opener)
        val popup = tabManager.openTabForWindow(openerId)
        val target = popup?.let { tabManager.session(it.id) }
        if (popup == null || target == null || !opener.respond(request.id, EngineResponse.AttachWindow(target))) {
            // Engine không nhận session mới làm đích: bỏ tab popup, quay về opener.
            popup?.let { closeAndForget(it.id) }
            tabManager.selectTab(openerId)
            opener.respond(request.id, EngineResponse.Dismiss())
            return
        }
        stopEditing()
        popupOpeners[popup.id] = openerId
    }

    private fun closeWindow(
        session: EngineSession,
        request: EngineRequest.CloseWindow,
    ) {
        session.respond(request.id, EngineResponse.Dismiss())
        val tabId = tabIdOf(session) ?: return
        // Chỉ tab mở bằng NewWindow mới được trang tự đóng (như Chrome); tab thường bỏ qua.
        val openerId = popupOpeners.remove(tabId) ?: return
        val wasSelected = tabId == tabManager.selectedTabId.value
        exitFullscreen(session)
        closeAndForget(tabId)
        if (wasSelected && tabManager.session(openerId) != null) tabManager.selectTab(openerId)
    }

    private fun permissionPlan(
        session: EngineSession,
        request: EngineRequest.PermissionPrompt,
    ): PermissionPlan =
        permissionPlans.getOrPut(session, ::HashMap).getOrPut(request.id) {
            // Bộ nhớ chỉ áp dụng khi origin xin quyền chính là trang chính (key thực tế = (trang, origin xin)).
            val rememberable = request.origin == originOf(session.state.value.url)
            val remembered =
                request.permissions.associateWith {
                    if (rememberable) permissionMemory.get(request.origin, it) else null
                }
            PermissionPlan(
                ask = remembered.filterValues { it == null }.keys,
                remembered = remembered.filterValues { it == true }.keys,
                rememberable = rememberable,
            )
        }

    private fun autoDecidePermission(
        session: EngineSession,
        request: EngineRequest.PermissionPrompt,
    ) {
        // AC17: chỉ origin an toàn mới được xin quyền; không dialog, không hỏi hệ thống.
        if (!isSecureOrigin(request.origin)) {
            session.respond(request.id, EngineResponse.Dismiss())
            return
        }
        val plan = permissionPlan(session, request)
        if (plan.ask.isEmpty() && !plan.inFlight) grantPermissions(session, request, plan, plan.remembered)
    }

    /**
     * Cấp [wanted] cho [request]: rỗng → `Dismiss()`; nếu không, xin hệ thống các quyền Android còn thiếu rồi
     * `Grant(<quyền hệ thống đã cấp>)`; thiếu quyền nào → [BrowserMessages.PERMISSION_DENIED].
     */
    private fun grantPermissions(
        session: EngineSession,
        request: EngineRequest.PermissionPrompt,
        plan: PermissionPlan,
        wanted: Set<SitePermission>,
    ) {
        if (wanted.isEmpty()) {
            session.respond(request.id, EngineResponse.Dismiss())
            return
        }
        plan.inFlight = true
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val missing = wanted.filterNot(::isOsGranted).flatMap { androidPermissions(it) }.toSet()
            val newlyGranted = if (missing.isEmpty()) emptySet() else runtimePermissions.request(missing)
            val granted =
                wanted.filterTo(mutableSetOf()) { p ->
                    isOsGranted(p) || androidPermissions(p).any { it in newlyGranted }
                }
            if (granted != wanted) message = BrowserMessages.PERMISSION_DENIED
            session.respond(request.id, EngineResponse.Grant(granted))
            recompute()
        }
    }

    /** Quyền Android đã cấp sẵn; Location chỉ cần một trong hai (fine/coarse). */
    private fun isOsGranted(permission: SitePermission): Boolean =
        androidPermissions(permission).any(runtimePermissions::isGranted)

    /**
     * Tab đang chọn ở home mà chưa dùng (spec 006 AC13): không có trang bên dưới để Back về và chưa từng rời
     * home (`backToHome`).
     */
    private fun unusedHomeTab(): String? {
        val id = tabManager.selectedTabId.value ?: return null
        val tab = tabManager.tabs.value.firstOrNull { it.id == id } ?: return null
        return id.takeIf { tab.isHome && !tab.backToHome && !tabManager.canNavigateBack(id) }
    }

    /**
     * Xử lý request `ExternalApp` mới của mọi session (spec 006 AC23–AC25 + review):
     * - URI bị launcher từ chối ([ExternalAppLauncher.describe] null) → bỏ, báo "blocked", không hỏi.
     * - Session đã có request đang chờ, hoặc vừa mở thẳng trong cùng đợt → bỏ ngay (chống spam).
     * - Có gesture, tab đang chọn, app hiển thị, và chưa mở thẳng trong [DIRECT_LAUNCH_INTERVAL_MS] → mở luôn.
     * - Không gesture sau khi người dùng hủy trên trang này → `Dismiss(suppressFurther = true)`.
     * - Còn lại giữ trong hàng đợi: tab đang chọn hiện hộp thoại, tab nền chờ tới khi được chọn.
     */
    private fun processExternalRequests() {
        val selected = current()
        for (session in observedSessions.orEmpty()) {
            val st = externalApps.getOrPut(session, ::ExternalAppState)
            if (st.suppressedAtUrl != null && st.suppressedAtUrl != session.state.value.url) st.suppressedAtUrl = null
            val pending = session.requests.value.filterIsInstance<EngineRequest.ExternalApp>()
            val seen = st.lastSeen
            val fresh = pending.filter { it.id > seen }
            if (fresh.isEmpty()) continue
            st.lastSeen = fresh.maxOf { it.id }
            var busy = pending.any { it.id <= seen }
            for (request in fresh) {
                if (request.hasUserGesture) st.suppressedAtUrl = null
                when {
                    externalAppLauncher.describe(request.uri) == null -> {
                        session.respond(request.id, EngineResponse.Dismiss())
                        message = BrowserMessages.EXTERNAL_APP_BLOCKED
                    }

                    busy -> {
                        session.respond(request.id, EngineResponse.Dismiss())
                    }

                    request.hasUserGesture && session === selected && canLaunchDirectly(st) -> {
                        session.respond(request.id, EngineResponse.Dismiss())
                        st.lastDirectLaunch = clock()
                        launchExternal(session, request)
                        busy = true
                    }

                    !request.hasUserGesture && st.suppressedAtUrl != null -> {
                        session.respond(request.id, EngineResponse.Dismiss(suppressFurther = true))
                    }

                    else -> {
                        busy = true
                    }
                }
            }
        }
    }

    private fun canLaunchDirectly(st: ExternalAppState): Boolean {
        val last = st.lastDirectLaunch
        return isAppVisible && (last == null || clock() - last >= DIRECT_LAUNCH_INTERVAL_MS)
    }

    private fun launchExternal(
        session: EngineSession,
        request: EngineRequest.ExternalApp,
    ) {
        when (val result = externalAppLauncher.launch(request.uri)) {
            ExternalLaunchResult.Launched -> {
                Unit
            }

            is ExternalLaunchResult.NotFound -> {
                val fallback = result.fallbackUrl?.takeIf(::isValidWebUrl)
                if (fallback != null) {
                    session.loadUrl(fallback)
                } else {
                    message = BrowserMessages.EXTERNAL_APP_NOT_FOUND
                }
            }

            is ExternalLaunchResult.OpenInBrowser -> {
                if (isValidWebUrl(result.url)) {
                    session.loadUrl(result.url)
                } else {
                    message = BrowserMessages.EXTERNAL_APP_BLOCKED
                }
            }

            ExternalLaunchResult.Rejected -> {
                message = BrowserMessages.EXTERNAL_APP_BLOCKED
            }
        }
    }

    private fun navigate(url: String) {
        stopEditing()
        tabManager.selectedTabId.value?.let { tabManager.loadUrl(it, url) }
    }

    private fun stopEditing() {
        isEditing = false
        suggestions = emptyList()
        suggestionJob?.cancel()
        suggestionJob = null
    }

    private fun closeAndForget(id: String) {
        tabManager.closeTab(id)
        pageVisits.forgetTab(id)
    }

    /** Theo dõi `state` của mọi session sống (title trong tab switcher, chrome của tab được chọn). */
    private fun observeSessions() {
        val sessions = tabManager.tabs.value.mapNotNull { tabManager.session(it.id) }
        // Gán trước khi launch: collector UNDISPATCHED gọi lại recompute() ngay và phải thấy danh sách mới.
        if (sessions == observedSessions) return
        observedSessions = sessions
        externalApps.keys.retainAll(sessions.toSet())
        permissionPlans.keys.retainAll(sessions.toSet())
        observePageVisits(sessions)
        sessionsJob?.cancel()
        sessionsJob =
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (sessions.isNotEmpty()) {
                    val flows = sessions.flatMap { listOf(it.state, it.requests) }
                    merge(*flows.toTypedArray()).collect { recompute() }
                }
            }
    }

    /** Mỗi session sống có một collector `pageFinished` → ghi lịch sử theo id/ẩn danh của tab sở hữu (spec 012). */
    private fun observePageVisits(sessions: List<EngineSession>) {
        visitJobs.keys.filter { it !in sessions }.forEach { visitJobs.remove(it)?.cancel() }
        for (tab in tabManager.tabs.value) {
            val session = tabManager.session(tab.id) ?: continue
            if (session in visitJobs) continue
            visitJobs[session] =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    session.pageFinished.collect {
                        pageVisits.record(PageVisit(tab.id, it.url, it.title, tab.isPrivate, it.isError))
                    }
                }
        }
    }

    /**
     * Tính lại [uiState]. Không tái nhập: phản hồi request / tải URL bên trong có thể làm flow của session phát
     * đồng bộ và gọi lại hàm này; lời gọi lồng chỉ đánh dấu để vòng ngoài tính lại.
     */
    private fun recompute() {
        if (recomputing) {
            recomputePending = true
            return
        }
        recomputing = true
        try {
            do {
                recomputePending = false
                observeSessions()
                processExternalRequests()
                processWebRequests()
                publishState()
            } while (recomputePending)
        } finally {
            recomputing = false
        }
    }

    private fun publishState() {
        // Engine có thể vừa tự điều hướng tab home chưa dùng (spec 007): lấy danh sách tab mới nhất.
        tabManager.refresh()
        val ids =
            tabManager.tabs.value
                .map { it.id }
                .toSet()
        externalTabIds.retainAll(ids)
        popupOpeners.keys.retainAll(ids)
        val selectedId = tabManager.selectedTabId.value
        val session = selectedId?.let(tabManager::session)
        val isHome = tabManager.tabs.value.any { it.id == selectedId && it.isHome }
        // Ở home không đồng bộ gì từ engine (trang bên dưới có thể vẫn đổi state).
        val st = session?.state?.value.takeUnless { isHome }
        val tabs =
            tabManager.tabs.value.map { tab ->
                if (tab.isHome) {
                    TabItem(id = tab.id, title = "", url = "", isHome = true, isPrivate = tab.isPrivate)
                } else {
                    val s = tabManager.session(tab.id)?.state?.value
                    TabItem(
                        id = tab.id,
                        title = s?.title?.ifEmpty { null } ?: tab.title,
                        url = s?.url?.ifEmpty { null } ?: tab.url,
                        isPrivate = tab.isPrivate,
                    )
                }
            }
        val url = if (isHome) "" else st?.url ?: tabs.firstOrNull { it.id == selectedId }?.url.orEmpty()
        val canBookmark = bookmarks != null && !isHome && isValidWebUrl(url)
        trackBookmark(url.takeIf { canBookmark })
        val isExternal = selectedId != null && selectedId in externalTabIds
        // Tab mở từ intent: Back ở gốc lịch sử engine quay về app gọi (spec 006 AC14) nên `backToHome` không tính.
        val canGoBack =
            when {
                selectedId == null -> false
                isExternal && !isHome -> st?.canGoBack == true
                else -> tabManager.canNavigateBack(selectedId)
            }
        val prompt =
            session
                ?.requests
                ?.value
                ?.firstNotNullOfOrNull { it as? EngineRequest.ExternalApp }
        _selectedSession.value = session
        _uiState.value =
            BrowserUiState(
                url = url,
                title = st?.title.orEmpty(),
                displayHost = UrlDisplay.host(url),
                isLoading = st?.isLoading == true && st.error == null,
                progress = st?.progress ?: 0,
                securityState = st?.securityState ?: BrowserUiState().securityState,
                canGoBack = canGoBack,
                canGoForward = st?.canGoForward == true,
                tabCount = tabs.size,
                tabs = tabs,
                selectedTabId = selectedId,
                isEditingUrl = isEditing,
                suggestions = suggestions,
                message = message,
                showSetDefaultBrowser = showSetDefaultBrowser,
                externalPrompt = prompt,
                externalPromptTabId = selectedId.takeIf { prompt != null },
                isExternalTab = isExternal,
                isHome = isHome,
                pendingRequest = session?.let(::pendingRequestOf),
                isFullscreen = st?.isFullscreen == true,
                canBookmark = canBookmark,
                isBookmarked = canBookmark && currentBookmark != null,
                bookmarkId = currentBookmark?.id.takeIf { canBookmark },
            )
    }

    /**
     * Request đầu tiên (FIFO) của [session] mà người dùng phải trả lời (spec 007 AC13). `PermissionPrompt` chỉ
     * liệt kê quyền chưa nhớ (AC16) và bị ẩn khi đang chờ hệ thống cấp quyền.
     */
    private fun pendingRequestOf(session: EngineSession): EngineRequest? =
        session.requests.value.firstNotNullOfOrNull { request ->
            when (request) {
                is EngineRequest.JsDialog,
                is EngineRequest.FileChooser,
                is EngineRequest.HttpAuth,
                is EngineRequest.Download,
                -> {
                    request
                }

                is EngineRequest.PermissionPrompt -> {
                    val plan = permissionPlans[session]?.get(request.id)
                    if (plan == null || plan.inFlight ||
                        plan.ask.isEmpty()
                    ) {
                        null
                    } else {
                        request.copy(permissions = plan.ask)
                    }
                }

                else -> {
                    null
                }
            }
        }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        val DOWNLOAD_SCHEMES = setOf("http", "https")

        fun androidPermissions(permission: SitePermission): Set<String> =
            when (permission) {
                SitePermission.Camera -> {
                    setOf(Manifest.permission.CAMERA)
                }

                SitePermission.Microphone -> {
                    setOf(Manifest.permission.RECORD_AUDIO)
                }

                SitePermission.Location -> {
                    setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }

        /** Tối đa một lần mở thẳng app ngoài mỗi khoảng này cho mỗi tab; request thêm phải qua hộp thoại. */
        const val DIRECT_LAUNCH_INTERVAL_MS = 1_000L
    }
}
