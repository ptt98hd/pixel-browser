package app.pixel.browser.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixel.browser.core.engine.EngineSession
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

    /** Ảnh chụp tab để caller ghi xuống đĩa ngoài main thread (spec 002). Gọi trên main thread. */
    fun tabsSnapshot(): TabsSnapshot = tabManager.snapshot()

    override fun onCleared() {
        tabManager.close()
    }

    private fun current(): EngineSession? = tabManager.selectedTabId.value?.let(tabManager::session)

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
        }
    }

    /** Theo dõi `state` của mọi session sống (title trong tab switcher, chrome của tab được chọn). */
    private fun observeSessions() {
        val sessions = tabManager.tabs.value.mapNotNull { tabManager.session(it.id) }
        // Gán trước khi launch: collector UNDISPATCHED gọi lại recompute() ngay và phải thấy danh sách mới.
        if (sessions == observedSessions) return
        observedSessions = sessions
        sessionsJob?.cancel()
        sessionsJob =
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (sessions.isNotEmpty()) merge(*sessions.map { it.state }.toTypedArray()).collect { recompute() }
            }
    }

    private fun recompute() {
        observeSessions()
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
            )
    }
}
