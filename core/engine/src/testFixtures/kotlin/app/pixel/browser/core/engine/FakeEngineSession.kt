package app.pixel.browser.core.engine

import android.graphics.Bitmap
import android.view.View
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Lệnh cuộn mà [FakeEngineSession] ghi lại (spec 005). */
enum class ScrollCommand { Top, Bottom }

/** Engine giả chạy trên JVM, tuân thủ cùng hợp đồng với [WebViewEngineSession]. */
class FakeEngineSession : EngineSession {
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state

    private val history = mutableListOf<String>()
    private var index = -1
    private var closed = false
    private var sslErrorInLoad = false
    private var mainFrameErrorInLoad = false
    private val _scrollCommands = mutableListOf<ScrollCommand>()

    val loadedUrls: List<String> get() = history.toList()

    val isClosed: Boolean get() = closed

    /**
     * Cache mode của điều hướng gần nhất (spec 003). Giống [WebViewEngineSession]: điều hướng lịch sử
     * dùng [CacheMode.CacheElseNetwork] cho tới khi trang tải xong, sau đó trở về [CacheMode.Default].
     */
    var cacheMode: CacheMode = CacheMode.Default
        private set

    override fun loadUrl(url: String) {
        if (closed) return
        if (!isRestorableUrl(url)) {
            _state.update { it.copy(error = "Invalid URL: $url", isLoading = false) }
            return
        }
        // Lịch sử chỉ đổi khi trang commit (simulatePageFinished) — giống engine thật.
        pendingNavigation = Navigation.NewPage
        cacheMode = CacheMode.Default
        sslErrorInLoad = false
        mainFrameErrorInLoad = false
        dialogGuard.onNewPage()
        _favicon.value = null
        _findState.value = FindState()
        val desktop = if (isValidWebUrl(url)) effectiveDesktop(url) else _state.value.isDesktopSite
        _state.update {
            it.copy(
                url = url,
                isLoading = true,
                progress = 0,
                error = null,
                securityState = SecurityState.Unknown,
                isAtTop = true,
                isDesktopSite = desktop,
            )
        }
    }

    private enum class Navigation { NewPage, Back, Forward, Reload }

    private var pendingNavigation = Navigation.NewPage

    override fun goBack() {
        if (closed || index <= 0) return
        pendingNavigation = Navigation.Back
        _findState.value = FindState()
        sslErrorInLoad = false
        mainFrameErrorInLoad = false
        dialogGuard.onNewPage()
        cacheMode = CacheMode.CacheElseNetwork
        _state.update { it.copy(url = history[index - 1], isLoading = true, progress = 0) }
    }

    override fun goForward() {
        if (closed || index >= history.lastIndex) return
        pendingNavigation = Navigation.Forward
        _findState.value = FindState()
        sslErrorInLoad = false
        mainFrameErrorInLoad = false
        dialogGuard.onNewPage()
        cacheMode = CacheMode.CacheElseNetwork
        _state.update { it.copy(url = history[index + 1], isLoading = true, progress = 0) }
    }

    override fun reload() {
        if (closed) return
        reloadCount++
        _findState.value = FindState()
        pendingNavigation = Navigation.Reload
        cacheMode = CacheMode.Default
        sslErrorInLoad = false
        mainFrameErrorInLoad = false
        dialogGuard.onNewPage()
        _state.update { it.copy(isLoading = true, progress = 0, isAtTop = true) }
    }

    /** Fake không có view thật (spec 005). */
    override val view: View? get() = null

    /** Các lệnh [scrollToTop]/[scrollToBottom] đã nhận, theo thứ tự (spec 005). */
    val scrollCommands: List<ScrollCommand> get() = _scrollCommands.toList()

    /** Trạng thái [onPause]/[onResume] gần nhất (review spec 005). */
    var isPaused: Boolean = false
        private set

    override fun onPause() {
        if (!closed) isPaused = true
    }

    override fun onResume() {
        if (!closed) isPaused = false
    }

    /** Như `onPageStarted` của WebView: không đổi lịch sử; securityState về Unknown trừ khi đã có lỗi SSL. */
    fun simulatePageStarted(url: String) {
        if (closed) return
        mainFrameErrorInLoad = false
        _favicon.value = null
        _findState.value = FindState()
        val security = if (sslErrorInLoad) SecurityState.Insecure else SecurityState.Unknown
        _state.update { it.copy(url = url, title = "", isLoading = true, securityState = security, isAtTop = true) }
        // Điều hướng lịch sử không qua shouldOverrideUrlLoading: chế độ hiệu lực khác hiện tại → đổi và reload một lần.
        val desktop = effectiveDesktop(url)
        if (desktop != _state.value.isDesktopSite) {
            _state.update { it.copy(isDesktopSite = desktop) }
            reloadCount++
            cacheMode = CacheMode.NoCache
        }
    }

    override fun scrollToTop() {
        if (!closed) _scrollCommands += ScrollCommand.Top
    }

    override fun scrollToBottom() {
        if (!closed) _scrollCommands += ScrollCommand.Bottom
    }

    override fun stopLoading() {
        if (closed) return
        _state.update { it.copy(isLoading = false) }
    }

    override fun saveState(): ByteArray? {
        if (closed || history.isEmpty()) return null
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(STATE_MAGIC)
            d.writeInt(index)
            d.writeUTF(_state.value.title)
            d.writeInt(history.size)
            history.forEach(d::writeUTF)
        }
        return out.toByteArray()
    }

    override fun restoreState(state: ByteArray): Boolean {
        if (closed) return false
        val (entries, current, title) = decode(state) ?: return false
        history.clear()
        history.addAll(entries)
        index = current
        // Engine tải lại mục hiện tại (từ cache) mà không đổi lịch sử.
        pendingNavigation = Navigation.Reload
        cacheMode = CacheMode.CacheElseNetwork
        sslErrorInLoad = false
        mainFrameErrorInLoad = false
        _findState.value = FindState()
        val desktop = effectiveDesktop(history[index])
        _state.update {
            it.copy(
                isDesktopSite = desktop,
                url = history[index],
                title = title,
                isLoading = true,
                progress = 0,
                error = null,
                isAtTop = true,
                canGoBack = index > 0,
                canGoForward = index < history.lastIndex,
            )
        }
        return true
    }

    private fun decode(bytes: ByteArray): Triple<List<String>, Int, String>? =
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { d ->
                if (d.readInt() != STATE_MAGIC) return null
                val current = d.readInt()
                val title = d.readUTF()
                val size = d.readInt()
                if (size !in 1..bytes.size || current !in 0 until size) return null
                val entries = List(size) { d.readUTF() }
                if (d.available() != 0 || !isRestorableUrl(entries[current])) return null
                Triple(entries, current, title)
            }
        } catch (_: IOException) {
            null
        }

    override fun close() {
        if (closed) return
        closed = true
        _favicon.value = null
        _findState.value = FindState()
        val pending = requestQueue.requests.value.map { it.id }
        requestQueue.dismissAll()
        pending.forEach { _responses[it] = EngineResponse.Dismiss() }
    }

    fun simulateProgress(progress: Int) {
        if (closed) return
        _state.update { it.copy(progress = clampProgress(progress)) }
    }

    fun simulatePageFinished(url: String) {
        if (closed) return
        when (pendingNavigation) {
            Navigation.NewPage -> {
                if (index < 0 || history[index] != url) {
                    while (history.lastIndex > index) history.removeAt(history.lastIndex)
                    history.add(url)
                    index = history.lastIndex
                }
            }

            Navigation.Back -> {
                index = (index - 1).coerceAtLeast(0)
            }

            Navigation.Forward -> {
                index = (index + 1).coerceAtMost(history.lastIndex)
            }

            Navigation.Reload -> {
                Unit
            }
        }
        pendingNavigation = Navigation.NewPage
        cacheMode = CacheMode.Default
        val security = securityStateOnFinish(url, sslErrorInLoad)
        val isError = mainFrameErrorInLoad || sslErrorInLoad
        sslErrorInLoad = false
        _state.update {
            it.copy(
                url = url,
                isLoading = false,
                progress = 100,
                securityState = security,
                canGoBack = index > 0,
                canGoForward = index < history.lastIndex,
            )
        }
        _pageFinished.tryEmit(PageFinished(url, _state.value.title, isError))
    }

    private val _pageFinished = MutableSharedFlow<PageFinished>(extraBufferCapacity = 8)
    override val pageFinished: SharedFlow<PageFinished> = _pageFinished

    /** Lỗi mạng/HTTP >= 400 ở main frame trong lần tải hiện tại (spec 012). */
    fun simulateMainFrameError() {
        if (closed) return
        mainFrameErrorInLoad = true
    }

    fun simulateSslError() {
        if (closed) return
        sslErrorInLoad = true
        _state.update { it.copy(securityState = SecurityState.Insecure) }
    }

    fun simulateTitle(title: String) {
        if (closed) return
        _state.update { it.copy(title = title) }
    }

    // --- Spec 006: EngineRequest ---

    private val requestQueue = EngineRequestQueue()
    private val _responses = linkedMapOf<Long, EngineResponse>()

    override val requests: StateFlow<List<EngineRequest>> = requestQueue.requests

    override fun respond(
        requestId: Long,
        response: EngineResponse,
    ): Boolean = requestQueue.respond(requestId, response).also { if (it) _responses[requestId] = response }

    /** Mọi response đã được chấp nhận (kể cả `dismissAll` khi close), theo id request. */
    val responses: Map<Long, EngineResponse> get() = _responses.toMap()

    /** Mô phỏng `shouldOverrideUrlLoading`; true = engine đã chặn điều hướng (spec 006 AC5). */
    fun simulateNavigation(
        url: String,
        isMainFrame: Boolean = true,
        hasUserGesture: Boolean = true,
        isRedirect: Boolean = false,
    ): Boolean {
        if (closed) return true
        val blocked = requestQueue.handleNavigation(_state.value.url, url, isMainFrame, hasUserGesture, isRedirect)
        if (!blocked && isMainFrame && isValidWebUrl(url)) {
            val desktop = effectiveDesktop(url)
            _state.update { it.copy(isDesktopSite = desktop) }
        }
        return blocked
    }

    // --- Spec 007: nền tảng web ---

    private val dialogGuard = JsDialogGuard()
    private var _exitFullscreenCalls = 0

    /** Fake không có view thật, kể cả khi fullscreen (spec 007 AC8). */
    override val fullscreenView: View? get() = null

    override fun exitFullscreen() {
        if (closed) return
        _exitFullscreenCalls++
        _state.update { it.copy(isFullscreen = false) }
    }

    /** Số lần [exitFullscreen] được gọi (spec 007 AC8, AC20). */
    val exitFullscreenCalls: Int get() = _exitFullscreenCalls

    private val pageOrigin: String get() = originOf(_state.value.url)

    /** Mô phỏng JS dialog từ trang hiện tại; null nếu bị chặn (response tự động ghi vào [responses]). */
    fun simulateJsDialog(
        kind: JsDialogKind,
        message: String,
        defaultValue: String? = null,
    ): EngineRequest.JsDialog? {
        if (closed) return null
        return requestQueue.enqueueJsDialog(
            guard = dialogGuard,
            kind = kind,
            origin = pageOrigin,
            message = message,
            defaultValue = defaultValue,
            onAutoResponse = { _responses[requestQueue.reserveId()] = it },
            deliver = {},
        )
    }

    fun simulatePermissionRequest(
        permissions: Set<SitePermission>,
        origin: String = originOf(state.value.url),
    ) {
        if (closed) return
        enqueueSimple { id -> EngineRequest.PermissionPrompt(id, origin, permissions) }
    }

    /** Engine tự hủy request (như `onPermissionRequestCanceled`): không ghi response. */
    fun simulatePermissionCanceled(id: Long) {
        requestQueue.cancel(id)
    }

    /** Như `onShowFileChooser`; chooser thứ hai khi đã có một cái đang chờ bị bỏ qua (spec 007 AC6). */
    fun simulateFileChooser(
        acceptTypes: List<String>,
        allowMultiple: Boolean = false,
        capture: Boolean = false,
    ) {
        if (closed || requestQueue.requests.value.any { it is EngineRequest.FileChooser }) return
        enqueueSimple { id ->
            EngineRequest.FileChooser(id, pageOrigin, normalizeAcceptTypes(acceptTypes), allowMultiple, capture)
        }
    }

    fun simulateHttpAuth(
        host: String,
        realm: String,
    ) {
        if (closed) return
        enqueueSimple { id -> EngineRequest.HttpAuth(id, pageOrigin, host, realm) }
    }

    /** Như `onCreateWindow`; true = popup được giữ (có gesture). */
    fun simulateCreateWindow(isUserGesture: Boolean): Boolean {
        if (closed) return false
        if (isUserGesture) {
            enqueueSimple { id -> EngineRequest.NewWindow(id, pageOrigin) }
        } else {
            enqueueSimple { id -> EngineRequest.PopupBlocked(id, pageOrigin) }
        }
        return isUserGesture
    }

    fun simulateCloseWindow() {
        if (closed) return
        enqueueSimple { id -> EngineRequest.CloseWindow(id, pageOrigin) }
    }

    fun simulateDownload(
        url: String,
        mimeType: String? = null,
        contentDisposition: String? = null,
        contentLength: Long = -1,
    ) {
        if (closed) return
        enqueueSimple { id -> EngineRequest.Download(id, pageOrigin, url, mimeType, contentDisposition, contentLength) }
    }

    /** Như `onShowCustomView`. */
    fun simulateEnterFullscreen() {
        if (closed) return
        _state.update { it.copy(isFullscreen = true) }
    }

    /** Như `onHideCustomView` (trang tự thoát fullscreen); không tính vào [exitFullscreenCalls]. */
    fun simulateExitFullscreen() {
        if (closed) return
        _state.update { it.copy(isFullscreen = false) }
    }

    private fun <T : EngineRequest> enqueueSimple(build: (Long) -> T) {
        requestQueue.enqueue(build) { request, response -> request.accepts(response) }
    }

    // --- Spec 008: cuộn / overscroll ---

    private val _topOverscroll = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val topOverscroll: SharedFlow<Unit> = _topOverscroll

    /** Như `onScrollChanged`: cập nhật `isAtTop = y == 0` (spec 008 AC1). */
    fun simulateScroll(y: Int) {
        if (closed) return
        _state.update { it.copy(isAtTop = y == 0) }
    }

    /** Như `onOverScrolled(scrollY = 0, clampedY = true)` (spec 008 AC2). */
    fun simulateOverscrollTop() {
        if (closed) return
        _topOverscroll.tryEmit(Unit)
    }

    // --- Spec 009: favicon ---

    private val _favicon = MutableStateFlow<Bitmap?>(null)
    override val favicon: StateFlow<Bitmap?> = _favicon

    /** Như `onReceivedIcon`; [bitmap] null = xóa favicon. */
    fun simulateFavicon(bitmap: Bitmap?) {
        if (!closed) _favicon.value = bitmap
    }

    // --- Spec 015 B04: tìm trong trang ---

    private val _findState = MutableStateFlow(FindState())
    override val findState: StateFlow<FindState> = _findState

    private val _findQueries = mutableListOf<String>()
    private val _findNextCalls = mutableListOf<Boolean>()

    /** Các truy vấn không rỗng đã gửi tới [find] (đã cắt còn [MAX_FIND_QUERY]), theo thứ tự. */
    val findQueries: List<String> get() = _findQueries.toList()

    /** Mọi lời gọi [findNext] (trước khi session đóng), theo thứ tự. */
    val findNextCalls: List<Boolean> get() = _findNextCalls.toList()

    /** Số lần [clearFind] được gọi tường minh (điều hướng chỉ đặt lại [findState], không tính). */
    var clearFindCalls: Int = 0
        private set

    override fun find(query: String) {
        if (closed) return
        val q = query.take(MAX_FIND_QUERY)
        if (q.isBlank()) {
            clearFind()
            return
        }
        _findQueries += q
        _findState.value = FindState(q, 0, 0, isDone = false)
    }

    override fun findNext(forward: Boolean) {
        if (closed) return
        _findNextCalls += forward
    }

    override fun clearFind() {
        if (closed) return
        clearFindCalls++
        _findState.value = FindState()
    }

    /** Như `FindListener.onFindResultReceived`; bỏ qua nếu không có truy vấn đang tìm (sau clear/close). */
    fun simulateFindResult(
        active: Int,
        total: Int,
        done: Boolean = true,
    ) {
        if (closed) return
        val current = _findState.value
        if (current.query.isEmpty()) return
        _findState.value = current.copy(activeIndex = active, total = total, isDone = done)
    }

    // --- Spec 015 B05: trang web máy tính ---

    private var resolver: DesktopSiteResolver? = null
    private val tempOverrides = mutableMapOf<String, Boolean>()

    /** Số lần tải lại (kể cả do [setDesktopSite] hoặc đổi chế độ khi điều hướng lịch sử). */
    var reloadCount: Int = 0
        private set

    override fun setDesktopSiteResolver(resolver: DesktopSiteResolver?) {
        if (!closed) this.resolver = resolver
    }

    override fun setDesktopSite(
        enabled: Boolean,
        rememberInSession: Boolean,
    ) {
        if (closed || _state.value.isDesktopSite == enabled) return
        val url = _state.value.url
        if (rememberInSession) siteKeyOf(url)?.let { tempOverrides[it] = enabled }
        _state.update { it.copy(isDesktopSite = enabled) }
        if (isValidWebUrl(url)) {
            reload()
            cacheMode = CacheMode.NoCache
        }
    }

    /** Chế độ hiệu lực của [url]: ngoại lệ tạm của session → resolver → di động. */
    private fun effectiveDesktop(url: String): Boolean {
        if (resolver == null && tempOverrides.isEmpty()) return false
        val key = siteKeyOf(url)
        return key?.let(tempOverrides::get) ?: resolver?.isDesktop(key) ?: false
    }

    private companion object {
        const val STATE_MAGIC = 0x50584653 // "PXFS"
    }
}
