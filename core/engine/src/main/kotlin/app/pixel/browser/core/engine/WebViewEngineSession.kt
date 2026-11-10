package app.pixel.browser.core.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * [EngineSession] chạy trên System WebView. Phải được dùng trên main thread.
 *
 * TODO(E05): `MissingOnRenderProcessGone` (lint của androidx.webkit, bật khi thêm dependency ở spec 016) được tắt
 * có chủ đích: xử lý render process chết (hủy + tạo lại WebView) là việc crash-safe của E05, không thuộc spec này.
 */
@SuppressLint("MissingOnRenderProcessGone")
class WebViewEngineSession(
    context: Context,
    privateProfile: String? = null,
    profileGateway: ProfileGateway = AndroidxProfileGateway,
) : EngineSession {
    override val isPrivate: Boolean = privateProfile != null
    private val pixel = createPixelWebView(context, privateProfile, profileGateway)
    val webView: WebView = pixel

    /** Giá trị WebSettings lúc khởi tạo, để chế độ di động khôi phục đúng (spec 015 AC17). */
    private val initialSettings =
        webView.settings.let {
            InitialSettings(
                it.userAgentString,
                it.useWideViewPort,
                it.loadWithOverviewMode,
                it.supportZoom(),
                it.builtInZoomControls,
                it.displayZoomControls,
            )
        }
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state

    var isClosed: Boolean = false
        private set

    private val requestQueue = EngineRequestQueue()

    /** Đã có lỗi SSL kể từ lần tải xong trước / điều hướng tường minh gần nhất (spec 005). */
    private var sslErrorInLoad = false
    private var mainFrameErrorInLoad = false

    private val dialogGuard = JsDialogGuard()

    /** Request quyền media đang chờ, để gỡ khi `onPermissionRequestCanceled` (spec 007 AC4). */
    private val pendingPermissions = mutableMapOf<PermissionRequest, Long>()

    /** Request vị trí đang chờ, để gỡ khi `onGeolocationPermissionsHidePrompt` (spec 007 AC5). */
    private val pendingGeolocation = mutableListOf<Long>()

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    init {
        pixel.owner = this
        configure(webView.settings)
        webView.setFindListener { active, total, done -> dispatchFindResult(active, total, done) }
        webView.webViewClient =
            object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: Bitmap?,
                ) = dispatchPageStarted(url)

                override fun onPageFinished(
                    view: WebView,
                    url: String,
                ) = dispatchPageFinished(url)

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean =
                    dispatchNavigation(
                        url = request.url.toString(),
                        isMainFrame = request.isForMainFrame,
                        hasGesture = request.hasGesture(),
                        isRedirect = request.isRedirect,
                    )

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String,
                    isReload: Boolean,
                ) = syncHistory()

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) dispatchMainFrameError()
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame && errorResponse.statusCode >= HTTP_ERROR_MIN) dispatchMainFrameError()
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError,
                ) {
                    handler.cancel()
                    dispatchSslError()
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView,
                    handler: HttpAuthHandler,
                    host: String,
                    realm: String,
                ) {
                    // Không dùng/ghi thông tin đăng nhập đã lưu (WebViewDatabase) – spec 007 AC7.
                    dispatchHttpAuth(host, realm) { response ->
                        if (response is EngineResponse.Credentials) {
                            handler.proceed(response.username, response.password)
                        } else {
                            handler.cancel()
                        }
                    }
                }
            }
        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(
                    view: WebView,
                    newProgress: Int,
                ) = dispatchProgress(newProgress)

                override fun onReceivedTitle(
                    view: WebView,
                    title: String,
                ) {
                    if (!isClosed) _state.update { it.copy(title = title) }
                }

                override fun onReceivedIcon(
                    view: WebView,
                    icon: Bitmap,
                ) = dispatchReceivedIcon(icon)

                override fun onJsAlert(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult,
                ): Boolean = handleJsDialog(JsDialogKind.Alert, url, message, null, result)

                override fun onJsConfirm(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult,
                ): Boolean = handleJsDialog(JsDialogKind.Confirm, url, message, null, result)

                override fun onJsPrompt(
                    view: WebView,
                    url: String,
                    message: String,
                    defaultValue: String?,
                    result: JsPromptResult,
                ): Boolean = handleJsDialog(JsDialogKind.Prompt, url, message, defaultValue, result)

                override fun onJsBeforeUnload(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult,
                ): Boolean = handleJsDialog(JsDialogKind.BeforeUnload, url, message, null, result)

                override fun onPermissionRequest(request: PermissionRequest) = handlePermissionRequest(request)

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    pendingPermissions.remove(request)?.let(requestQueue::cancel)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String,
                    callback: GeolocationPermissions.Callback,
                ) = handleGeolocationPrompt(origin, callback)

                override fun onGeolocationPermissionsHidePrompt() {
                    pendingGeolocation.forEach(requestQueue::cancel)
                    pendingGeolocation.clear()
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams,
                ): Boolean = handleFileChooser(filePathCallback, fileChooserParams)

                override fun onShowCustomView(
                    view: View,
                    callback: CustomViewCallback,
                ) = handleShowCustomView(view, callback)

                override fun onHideCustomView() = handleHideCustomView()

                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message,
                ): Boolean =
                    dispatchCreateWindow(isUserGesture) { target ->
                        (resultMsg.obj as? WebView.WebViewTransport)?.webView = target
                        resultMsg.sendToTarget()
                    }

                override fun onCloseWindow(window: WebView) {
                    if (isClosed) return
                    enqueueSimple { id -> EngineRequest.CloseWindow(id, pageOrigin) }
                }
            }
        webView.setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
            // Engine không tự tải: tầng UI quyết định (spec 007 AC10).
            if (!isClosed) {
                enqueueSimple { id ->
                    EngineRequest.Download(id, pageOrigin, url, mimeType, contentDisposition, contentLength)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(s: WebSettings) {
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.safeBrowsingEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        // Popup: WebView luôn báo onCreateWindow, engine tự quyết theo isUserGesture (spec 007 AC9).
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setGeolocationEnabled(true)
        s.mediaPlaybackRequiresUserGesture = true
    }

    override fun loadUrl(url: String) {
        if (isClosed) return
        if (!isRestorableUrl(url)) {
            _state.update { it.copy(error = "Invalid URL: $url", isLoading = false) }
            return
        }
        sslErrorInLoad = false
        mainFrameErrorInLoad = false
        dialogGuard.onNewPage()
        _favicon.value = null
        resetFind()
        modeReloadedFor = null
        if (isValidWebUrl(url)) applyEffectiveDesktop(url)
        _state.update {
            it.copy(
                url = url,
                isLoading = true,
                progress = 0,
                error = null,
                securityState = SecurityState.Unknown,
                isAtTop = true,
            )
        }
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.loadUrl(url)
    }

    override fun goBack() {
        if (isClosed || !webView.canGoBack()) return
        sslErrorInLoad = false
        mainFrameErrorInLoad = false
        resetFind()
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webView.goBack()
    }

    override fun goForward() {
        if (isClosed || !webView.canGoForward()) return
        sslErrorInLoad = false
        mainFrameErrorInLoad = false
        resetFind()
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webView.goForward()
    }

    override fun reload() {
        if (isClosed) return
        sslErrorInLoad = false
        mainFrameErrorInLoad = false
        dialogGuard.onNewPage()
        resetFind()
        _state.update { it.copy(isLoading = true, progress = 0, isAtTop = true) }
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.reload()
    }

    override val view: View?
        get() = if (isClosed) null else webView

    override fun onPause() {
        if (!isClosed) webView.onPause()
    }

    override fun onResume() {
        if (!isClosed) webView.onResume()
    }

    override fun scrollToTop() {
        if (isClosed) return
        webView.pageUp(true)
    }

    override fun scrollToBottom() {
        if (isClosed) return
        webView.pageDown(true)
    }

    override fun stopLoading() {
        if (isClosed) return
        webView.stopLoading()
        _state.update { it.copy(isLoading = false) }
    }

    /**
     * Bundle của `WebView.saveState` được marshall bằng [Parcel]. Định dạng Parcel không ổn định giữa các
     * phiên bản Android/WebView: chỉ dùng cho discard trong process và process death cùng phiên bản;
     * nếu khôi phục thất bại, tầng trên tải lại URL.
     */
    override fun saveState(): ByteArray? {
        if (isClosed) return null
        return try {
            val bundle = Bundle()
            val list = webView.saveState(bundle)
            if (list == null || list.size == 0 || bundle.isEmpty) null else marshall(bundle)
        } catch (_: RuntimeException) {
            null
        }
    }

    override fun restoreState(state: ByteArray): Boolean {
        if (isClosed) return false
        val bundle = unmarshall(state) ?: return false
        val previousMode = webView.settings.cacheMode
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        val list =
            try {
                webView.restoreState(bundle)
            } catch (_: RuntimeException) {
                null
            }
        val current = list?.currentItem
        if (list == null || list.size == 0 || current == null || !isRestorableUrl(current.url)) {
            // Mục hiện tại không phải URL web (vd. file://, javascript:): dừng tải, giữ nguyên state.
            if (list != null) webView.stopLoading()
            webView.settings.cacheMode = previousMode
            return false
        }
        sslErrorInLoad = false
        _state.update {
            it.copy(
                url = current.url,
                title = current.title.orEmpty(),
                isLoading = true,
                progress = 0,
                error = null,
                securityState = SecurityState.Unknown,
                isAtTop = true,
                canGoBack = webView.canGoBack(),
                canGoForward = webView.canGoForward(),
            )
        }
        return true
    }

    override fun close() {
        if (isClosed) return
        exitFullscreen()
        handleHideCustomView()
        isClosed = true
        _favicon.value = null
        _findState.value = FindState()
        requestQueue.dismissAll()
        pendingPermissions.clear()
        pendingGeolocation.clear()
        webView.stopLoading()
        // Gỡ khỏi cây view và trả context về application trước destroy để không giữ Activity (spec 005).
        (webView.parent as? ViewGroup)?.removeView(webView)
        (webView.context as? MutableContextWrapper)?.let { it.baseContext = it.applicationContext }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = null
        webView.setDownloadListener(null)
        webView.setFindListener(null)
        pixel.owner = null
        webView.destroy()
    }

    // Điểm vào của callback WebView; internal để test mô phỏng.
    fun dispatchPageStarted(url: String) {
        if (isClosed) return
        mainFrameErrorInLoad = false
        dialogGuard.onNewPage()
        _favicon.value = null
        resetFind()
        val security = if (sslErrorInLoad) SecurityState.Insecure else SecurityState.Unknown
        _state.update { it.copy(url = url, title = "", isLoading = true, securityState = security, isAtTop = true) }
        // Điều hướng lịch sử/restore không qua shouldOverrideUrlLoading: đổi chế độ rồi reload một lần (AC19).
        val desktop = effectiveDesktop(url)
        if (desktop != _state.value.isDesktopSite && modeReloadedFor != url) {
            modeReloadedFor = url
            applyDesktopMode(desktop)
            reloadNoCache()
        }
    }

    fun dispatchPageFinished(url: String) {
        if (isClosed) return
        // Cache-first chỉ áp dụng cho chính điều hướng lịch sử; điều hướng sau đó (vd. bấm link) dùng mặc định.
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        val security = securityStateOnFinish(url, sslErrorInLoad)
        val isError = mainFrameErrorInLoad || sslErrorInLoad
        sslErrorInLoad = false
        _state.update { it.copy(url = url, isLoading = false, progress = 100, securityState = security) }
        syncHistory()
        _pageFinished.tryEmit(PageFinished(url, _state.value.title, isError))
    }

    private val _pageFinished = MutableSharedFlow<PageFinished>(extraBufferCapacity = 8)
    override val pageFinished: SharedFlow<PageFinished> = _pageFinished

    /** Spec 012: main frame lỗi mạng/HTTP >= 400. */
    fun dispatchMainFrameError() {
        if (isClosed) return
        mainFrameErrorInLoad = true
    }

    fun dispatchProgress(progress: Int) {
        if (isClosed) return
        _state.update { it.copy(progress = clampProgress(progress)) }
    }

    fun dispatchSslError() {
        if (isClosed) return
        sslErrorInLoad = true
        _state.update { it.copy(securityState = SecurityState.Insecure) }
    }

    override val requests: StateFlow<List<EngineRequest>> = requestQueue.requests

    override fun respond(
        requestId: Long,
        response: EngineResponse,
    ): Boolean = requestQueue.respond(requestId, response)

    /** Điểm vào của `shouldOverrideUrlLoading` (spec 006 AC5). true = engine chặn điều hướng. */
    fun dispatchNavigation(
        url: String,
        isMainFrame: Boolean,
        hasGesture: Boolean,
        isRedirect: Boolean,
    ): Boolean {
        if (isClosed) return true
        val blocked = requestQueue.handleNavigation(_state.value.url, url, isMainFrame, hasGesture, isRedirect)
        // Chỉ URL web đổi chế độ: javascript:/data:/blob: không phải điều hướng sang site khác.
        if (!blocked && isMainFrame && isValidWebUrl(url)) {
            modeReloadedFor = null
            applyEffectiveDesktop(url)
        }
        return blocked
    }

    override val fullscreenView: View?
        get() = customView

    override fun exitFullscreen() {
        val callback = customViewCallback ?: return
        customViewCallback = null
        customView = null
        callback.onCustomViewHidden()
        _state.update { it.copy(isFullscreen = false) }
    }

    /** Điểm vào của `onJsAlert/onJsConfirm/onJsPrompt/onJsBeforeUnload` (spec 007 AC2–AC3). */
    fun dispatchJsDialog(
        kind: JsDialogKind,
        url: String,
        message: String,
        defaultValue: String?,
        onResult: (EngineResponse) -> Unit,
    ) {
        if (isClosed) {
            onResult(EngineResponse.Dismiss())
            return
        }
        requestQueue.enqueueJsDialog(
            guard = dialogGuard,
            kind = kind,
            origin = originOf(url),
            message = message,
            defaultValue = defaultValue,
            onAutoResponse = onResult,
            deliver = onResult,
        )
    }

    /** Điểm vào của `onReceivedHttpAuthRequest` (spec 007 AC7). */
    fun dispatchHttpAuth(
        host: String,
        realm: String,
        onResult: (EngineResponse) -> Unit,
    ) {
        if (isClosed) {
            onResult(EngineResponse.Dismiss())
            return
        }
        requestQueue.enqueue(
            build = { id -> EngineRequest.HttpAuth(id, pageOrigin, host, realm) },
            onResponse = { request, response ->
                request.accepts(response).also { if (it) onResult(response) }
            },
        )
    }

    /** Điểm vào của `onCreateWindow` (spec 007 AC9). true = engine giữ popup (chờ [onAttach]). */
    fun dispatchCreateWindow(
        isUserGesture: Boolean,
        onAttach: (WebView) -> Unit,
    ): Boolean {
        if (isClosed) return false
        if (!isUserGesture) {
            enqueueSimple { id -> EngineRequest.PopupBlocked(id, pageOrigin) }
            return false
        }
        requestQueue.enqueue(
            build = { id -> EngineRequest.NewWindow(id, pageOrigin) },
            onResponse = { request, response ->
                when (response) {
                    is EngineResponse.AttachWindow -> {
                        val target = response.target as? WebViewEngineSession
                        if (target == null || target.isClosed || target === this) {
                            false
                        } else {
                            onAttach(target.webView)
                            true
                        }
                    }

                    else -> {
                        request.accepts(response)
                    }
                }
            },
        )
        return true
    }

    // --- Spec 008: cuộn / overscroll ---

    private val _topOverscroll = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val topOverscroll: SharedFlow<Unit> = _topOverscroll

    /** Điểm vào của `onScrollChanged` (spec 008 AC1). */
    fun dispatchScrollChanged(scrollY: Int) {
        if (isClosed) return
        val atTop = scrollY == 0
        _state.update { if (it.isAtTop == atTop) it else it.copy(isAtTop = atTop) }
    }

    /** Điểm vào của `onOverScrolled` (spec 008 AC2). */
    fun dispatchOverScrolled(
        scrollY: Int,
        clampedY: Boolean,
    ) {
        if (isClosed) return
        if (clampedY && scrollY == 0) _topOverscroll.tryEmit(Unit)
    }

    // --- Spec 009: favicon ---

    private val _favicon = MutableStateFlow<Bitmap?>(null)
    override val favicon: StateFlow<Bitmap?> = _favicon

    /** Điểm vào của `WebChromeClient.onReceivedIcon`. */
    fun dispatchReceivedIcon(bitmap: Bitmap) {
        if (!isClosed) _favicon.value = bitmap
    }

    // --- Spec 015 B04: tìm trong trang ---

    private val _findState = MutableStateFlow(FindState())
    override val findState: StateFlow<FindState> = _findState

    override fun find(query: String) {
        if (isClosed) return
        val q = query.take(MAX_FIND_QUERY)
        if (q.isBlank()) {
            clearFind()
            return
        }
        _findState.value = FindState(q, 0, 0, isDone = false)
        webView.findAllAsync(q)
    }

    override fun findNext(forward: Boolean) {
        if (isClosed || _findState.value.total == 0) return
        webView.findNext(forward)
    }

    override fun clearFind() {
        if (isClosed) return
        resetFind()
    }

    private fun resetFind() {
        _findState.value = FindState()
        webView.clearMatches()
    }

    /** Điểm vào của `WebView.FindListener` (spec 015 AC1/AC3). Bỏ qua khi không có truy vấn (sau clear/close). */
    fun dispatchFindResult(
        activeMatchOrdinal: Int,
        numberOfMatches: Int,
        isDoneCounting: Boolean,
    ) {
        if (isClosed) return
        val current = _findState.value
        if (current.query.isEmpty()) return
        _findState.value =
            current.copy(activeIndex = activeMatchOrdinal, total = numberOfMatches, isDone = isDoneCounting)
    }

    // --- Spec 015 B05: trang web máy tính ---

    private var desktopResolver: DesktopSiteResolver? = null
    private val tempOverrides = mutableMapOf<String, Boolean>()

    /** URL đã reload một lần để đổi chế độ khi điều hướng lịch sử (chống vòng lặp). */
    private var modeReloadedFor: String? = null

    override fun setDesktopSiteResolver(resolver: DesktopSiteResolver?) {
        if (!isClosed) desktopResolver = resolver
    }

    override fun setDesktopSite(
        enabled: Boolean,
        rememberInSession: Boolean,
    ) {
        if (isClosed || _state.value.isDesktopSite == enabled) return
        val url = _state.value.url
        if (rememberInSession) siteKeyOf(url)?.let { tempOverrides[it] = enabled }
        applyDesktopMode(enabled)
        if (isValidWebUrl(url)) reloadNoCache()
    }

    /** Chế độ hiệu lực của [url]: ngoại lệ tạm của session → resolver → di động. */
    private fun effectiveDesktop(url: String): Boolean {
        val key = siteKeyOf(url)
        return key?.let(tempOverrides::get) ?: desktopResolver?.isDesktop(key) ?: false
    }

    private fun applyDesktopMode(desktop: Boolean) {
        val s = webView.settings
        if (desktop) {
            s.userAgentString = DesktopUserAgent.derive(initialSettings.userAgent)
            s.useWideViewPort = true
            s.loadWithOverviewMode = true
            s.setSupportZoom(true)
            s.builtInZoomControls = true
            s.displayZoomControls = false
        } else {
            s.userAgentString = null
            s.useWideViewPort = initialSettings.wideViewPort
            s.loadWithOverviewMode = initialSettings.overviewMode
            s.setSupportZoom(initialSettings.supportZoom)
            s.builtInZoomControls = initialSettings.builtInZoomControls
            s.displayZoomControls = initialSettings.displayZoomControls
        }
        _state.update { it.copy(isDesktopSite = desktop) }
    }

    private fun applyEffectiveDesktop(url: String) {
        val desktop = effectiveDesktop(url)
        if (desktop != _state.value.isDesktopSite) applyDesktopMode(desktop)
    }

    private fun reloadNoCache() {
        reload()
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
    }

    private class InitialSettings(
        val userAgent: String,
        val wideViewPort: Boolean,
        val overviewMode: Boolean,
        val supportZoom: Boolean,
        val builtInZoomControls: Boolean,
        val displayZoomControls: Boolean,
    )

    private val pageOrigin: String get() = originOf(_state.value.url)

    private fun <T : EngineRequest> enqueueSimple(build: (Long) -> T) {
        requestQueue.enqueue(build) { request, response -> request.accepts(response) }
    }

    private fun handleJsDialog(
        kind: JsDialogKind,
        url: String,
        message: String,
        defaultValue: String?,
        result: JsResult,
    ): Boolean {
        dispatchJsDialog(kind, url, message, defaultValue) { response ->
            when (response) {
                is EngineResponse.PromptText -> {
                    (result as? JsPromptResult)?.confirm(response.value) ?: result.confirm()
                }

                // Prompt + Confirm = giá trị mặc định (AC1).
                is EngineResponse.Confirm -> {
                    (result as? JsPromptResult)?.confirm(defaultValue.orEmpty())
                        ?: result.confirm()
                }

                else -> {
                    result.cancel()
                }
            }
        }
        return true
    }

    private fun handlePermissionRequest(request: PermissionRequest) {
        val permissions = request.resources.mapNotNull(MEDIA_RESOURCES::get).toSet()
        if (isClosed || permissions.isEmpty()) {
            request.deny()
            return
        }
        val prompt =
            requestQueue.enqueue(
                build = { id ->
                    EngineRequest.PermissionPrompt(id, originOf(request.origin.toString()), permissions)
                },
                onResponse = { prompt, response ->
                    if (!prompt.accepts(response)) return@enqueue false
                    pendingPermissions.remove(request)
                    val granted = (response as? EngineResponse.Grant)?.permissions.orEmpty()
                    val resources = MEDIA_RESOURCES.filterValues { it in granted }.keys
                    if (resources.isEmpty()) request.deny() else request.grant(resources.toTypedArray())
                    true
                },
            )
        pendingPermissions[request] = prompt.id
    }

    private fun handleGeolocationPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        if (isClosed) {
            callback.invoke(origin, false, false)
            return
        }
        val prompt =
            requestQueue.enqueue(
                build = { id -> EngineRequest.PermissionPrompt(id, originOf(origin), setOf(SitePermission.Location)) },
                onResponse = { prompt, response ->
                    if (!prompt.accepts(response)) return@enqueue false
                    pendingGeolocation.remove(prompt.id)
                    val allow =
                        (response as? EngineResponse.Grant)?.permissions?.contains(SitePermission.Location) == true
                    // Không bao giờ retain: quyền chỉ nhớ trong process ở tầng UI (spec 007 AC5, AC16).
                    callback.invoke(origin, allow, false)
                    true
                },
            )
        pendingGeolocation += prompt.id
    }

    private fun handleFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        // Luôn phải gọi callback (kể cả null), nếu không WebView không mở chooser lần sau (spec 007 AC6).
        if (isClosed || requestQueue.requests.value.any { it is EngineRequest.FileChooser }) {
            callback.onReceiveValue(null)
            return true
        }
        requestQueue.enqueue(
            build = { id ->
                EngineRequest.FileChooser(
                    id = id,
                    origin = pageOrigin,
                    acceptTypes = normalizeAcceptTypes(params.acceptTypes.orEmpty().toList()),
                    allowMultiple = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE,
                    capture = params.isCaptureEnabled,
                )
            },
            onResponse = { request, response ->
                if (!request.accepts(response)) return@enqueue false
                // Chỉ chuyển URI content:// cho trang; file://, đường dẫn trần, http… bị bỏ (review spec 007).
                val uris =
                    (response as? EngineResponse.Files)
                        ?.uris
                        .orEmpty()
                        .map(Uri::parse)
                        .filter { it.scheme.equals("content", ignoreCase = true) }
                callback.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
                true
            },
        )
        return true
    }

    private fun handleShowCustomView(
        view: View,
        callback: WebChromeClient.CustomViewCallback,
    ) {
        if (isClosed || customView != null) {
            // Đang fullscreen: từ chối view mới, giữ view cũ (spec 007 AC8).
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        _state.update { it.copy(isFullscreen = true) }
    }

    private fun handleHideCustomView() {
        customView = null
        customViewCallback = null
        if (!isClosed) _state.update { it.copy(isFullscreen = false) }
    }

    private fun syncHistory() {
        if (isClosed) return
        _state.update { it.copy(canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward()) }
    }

    private companion object {
        const val HTTP_ERROR_MIN = 400
        const val STATE_MAGIC = 0x50585753 // "PXWS"

        /** Resource media được hỗ trợ; MIDI/Protected media bị từ chối (spec 007 AC4). */
        val MEDIA_RESOURCES =
            mapOf(
                PermissionRequest.RESOURCE_VIDEO_CAPTURE to SitePermission.Camera,
                PermissionRequest.RESOURCE_AUDIO_CAPTURE to SitePermission.Microphone,
            )
        const val STATE_VERSION = 1

        fun marshall(bundle: Bundle): ByteArray {
            val parcel = Parcel.obtain()
            try {
                parcel.writeInt(STATE_MAGIC)
                parcel.writeInt(STATE_VERSION)
                parcel.writeBundle(bundle)
                return parcel.marshall()
            } finally {
                parcel.recycle()
            }
        }

        fun unmarshall(bytes: ByteArray): Bundle? {
            if (bytes.size < 2 * Int.SIZE_BYTES) return null
            val parcel = Parcel.obtain()
            return try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                if (parcel.readInt() != STATE_MAGIC || parcel.readInt() != STATE_VERSION) return null
                parcel.readBundle(WebViewEngineSession::class.java.classLoader)?.takeUnless { it.isEmpty }
            } catch (_: RuntimeException) {
                null
            } finally {
                parcel.recycle()
            }
        }
    }
}

/**
 * Tạo [PixelWebView]; với [privateProfile] áp profile **ngay** sau khi tạo (trước mọi thao tác khác). Không áp được
 * → hủy WebView và ném [PrivateUnavailableException] (fail-closed, không rơi về profile mặc định; spec 016 AC6).
 */
private fun createPixelWebView(
    context: Context,
    privateProfile: String?,
    gateway: ProfileGateway,
): PixelWebView {
    if (privateProfile == null) return PixelWebView(context)
    if (!gateway.isSupported) throw PrivateUnavailableException("MULTI_PROFILE is not supported")
    val view = PixelWebView(context).also { it.isPrivate = true }
    try {
        gateway.apply(view, privateProfile)
    } catch (e: RuntimeException) {
        view.destroy()
        throw PrivateUnavailableException("Cannot apply private profile: ${e.message}")
    }
    view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    return view
}
