package app.pixel.browser.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.isValidWebUrl
import app.pixel.browser.core.omnibox.DefaultSuggestionProvider
import app.pixel.browser.core.omnibox.OmniboxClassifier
import app.pixel.browser.core.omnibox.OmniboxResult
import app.pixel.browser.core.omnibox.SearchEngine
import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.Suggestion
import app.pixel.browser.core.omnibox.SuggestionProvider
import app.pixel.browser.core.omnibox.UrlDisplay
import app.pixel.browser.core.omnibox.homeUrl
import app.pixel.browser.core.tabs.HOME_URL
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.intent.ExternalAppLauncher
import app.pixel.browser.feature.browser.intent.ExternalLaunchResult
import app.pixel.browser.feature.browser.intent.IncomingRequest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}

/**
 * State holder của màn hình trình duyệt (spec 005). Kết hợp [TabManager] với `state` của session được chọn.
 * Khi khởi tạo, nếu tab được chọn chưa tải gì thì tải [homeUrl].
 *
 * Phải dùng trên main thread (như [TabManager]). [uiState] được tính lại đồng bộ từ giá trị hiện tại của
 * [TabManager] và các session mỗi khi một trong các flow đó phát, hoặc sau mỗi hành động.
 */
class BrowserViewModel(
    private val tabManager: TabManager,
    val searchEngine: SearchEngine = SearchEngines.Default,
    private val suggestionProvider: SuggestionProvider = DefaultSuggestionProvider(searchEngine),
    private val externalAppLauncher: ExternalAppLauncher = ExternalAppLauncher { ExternalLaunchResult.Rejected },
    /** Đồng hồ đơn điệu (ms) cho throttle mở app ngoài. */
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : ViewModel() {
    /** Trang home tạm thời = trang chủ search engine (spec 004 AC18) cho tới A10. */
    val homeUrl: String get() = searchEngine.homeUrl

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

    /** Tab home mà ViewModel đã tự tải [homeUrl] vào; được tái dùng cho intent nếu chưa có lịch sử (AC13). */
    private var homeTabId: String? = null

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
    private var isAppVisible = true
    private var showSetDefaultBrowser = false
    private var recomputing = false
    private var recomputePending = false

    init {
        ensureSelectedLoaded()
        recompute()
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            merge(tabManager.tabs, tabManager.selectedTabId).collect {
                ensureSelectedLoaded()
                recompute()
            }
        }
    }

    fun onBack() {
        val s = current() ?: return
        if (s.state.value.canGoBack) s.goBack()
    }

    fun onForward() {
        val s = current() ?: return
        if (s.state.value.canGoForward) s.goForward()
    }

    fun onHome() {
        stopEditing()
        current()?.loadUrl(homeUrl)
        recompute()
    }

    fun onReload() {
        current()?.reload()
        recompute()
    }

    fun onNewTab() {
        stopEditing()
        tabManager.openTab(homeUrl)
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

    fun onSuggestionClick(suggestion: Suggestion) {
        navigate(suggestion.url)
        recompute()
    }

    fun onSelectTab(id: String) {
        stopEditing()
        tabManager.selectTab(id)
        recompute()
    }

    fun onCloseTab(id: String) {
        tabManager.closeTab(id)
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
        if (url == null || !isValidWebUrl(url)) {
            message = BrowserMessages.INTENT_REJECTED
        } else {
            val reusable = unusedHomeTab()
            val id =
                if (reusable != null) {
                    tabManager.session(reusable)?.loadUrl(url)
                    reusable
                } else {
                    tabManager.openTab(url).id
                }
            homeTabId = null
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
        tabManager.closeTab(id)
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

    /** Tab home ban đầu, đang chọn, chưa có lịch sử và chưa rời [homeUrl] (AC13). */
    private fun unusedHomeTab(): String? {
        val id = homeTabId ?: return null
        if (id != tabManager.selectedTabId.value) return null
        val st = tabManager.session(id)?.state?.value ?: return null
        return id.takeIf { !st.canGoBack && (st.url.isEmpty() || st.url == homeUrl) }
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
        current()?.loadUrl(url)
    }

    private fun stopEditing() {
        isEditing = false
        suggestions = emptyList()
        suggestionJob?.cancel()
        suggestionJob = null
    }

    /** Tab home mới (initial, tạo lại sau khi đóng tab cuối, khôi phục) chưa tải gì → tải [homeUrl]. */
    private fun ensureSelectedLoaded() {
        val id = tabManager.selectedTabId.value ?: return
        val tab = tabManager.tabs.value.firstOrNull { it.id == id } ?: return
        val session = tabManager.session(id) ?: return
        val st = session.state.value
        if (tab.url == HOME_URL && st.url.isEmpty() && !st.isLoading && st.error == null) {
            session.loadUrl(homeUrl)
            homeTabId = id
        }
    }

    /** Theo dõi `state` của mọi session sống (title trong tab switcher, chrome của tab được chọn). */
    private fun observeSessions() {
        val sessions = tabManager.tabs.value.mapNotNull { tabManager.session(it.id) }
        // Gán trước khi launch: collector UNDISPATCHED gọi lại recompute() ngay và phải thấy danh sách mới.
        if (sessions == observedSessions) return
        observedSessions = sessions
        externalApps.keys.retainAll(sessions.toSet())
        sessionsJob?.cancel()
        sessionsJob =
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (sessions.isNotEmpty()) {
                    val flows = sessions.flatMap { listOf(it.state, it.requests) }
                    merge(*flows.toTypedArray()).collect { recompute() }
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
                publishState()
            } while (recomputePending)
        } finally {
            recomputing = false
        }
    }

    private fun publishState() {
        val ids =
            tabManager.tabs.value
                .map { it.id }
                .toSet()
        externalTabIds.retainAll(ids)
        val selectedId = tabManager.selectedTabId.value
        val session = selectedId?.let(tabManager::session)
        val st = session?.state?.value
        val tabs =
            tabManager.tabs.value.map { tab ->
                val s = tabManager.session(tab.id)?.state?.value
                TabItem(
                    id = tab.id,
                    title = s?.title?.ifEmpty { null } ?: tab.title,
                    url = s?.url?.ifEmpty { null } ?: tab.url,
                )
            }
        val url = st?.url ?: tabs.firstOrNull { it.id == selectedId }?.url.orEmpty()
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
                canGoBack = st?.canGoBack == true,
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
                isExternalTab = selectedId != null && selectedId in externalTabIds,
            )
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /** Tối đa một lần mở thẳng app ngoài mỗi khoảng này cho mỗi tab; request thêm phải qua hộp thoại. */
        const val DIRECT_LAUNCH_INTERVAL_MS = 1_000L
    }
}
