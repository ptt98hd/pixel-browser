package app.pixel.browser.core.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** [EngineSession] chạy trên System WebView. Phải được dùng trên main thread. */
class WebViewEngineSession(
    context: Context,
) : EngineSession {
    val webView: WebView = WebView(context)
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state

    var isClosed: Boolean = false
        private set

    /** Đã có lỗi SSL kể từ lần tải xong trước / điều hướng tường minh gần nhất (spec 005). */
    private var sslErrorInLoad = false

    init {
        configure(webView.settings)
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

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String,
                    isReload: Boolean,
                ) = syncHistory()

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError,
                ) {
                    handler.cancel()
                    dispatchSslError()
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
    }

    override fun loadUrl(url: String) {
        if (isClosed) return
        if (!isRestorableUrl(url)) {
            _state.update { it.copy(error = "Invalid URL: $url", isLoading = false) }
            return
        }
        sslErrorInLoad = false
        _state.update {
            it.copy(url = url, isLoading = true, progress = 0, error = null, securityState = SecurityState.Unknown)
        }
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.loadUrl(url)
    }

    override fun goBack() {
        if (isClosed || !webView.canGoBack()) return
        sslErrorInLoad = false
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webView.goBack()
    }

    override fun goForward() {
        if (isClosed || !webView.canGoForward()) return
        sslErrorInLoad = false
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webView.goForward()
    }

    override fun reload() {
        if (isClosed) return
        sslErrorInLoad = false
        _state.update { it.copy(isLoading = true, progress = 0) }
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
                canGoBack = webView.canGoBack(),
                canGoForward = webView.canGoForward(),
            )
        }
        return true
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        webView.stopLoading()
        // Gỡ khỏi cây view và trả context về application trước destroy để không giữ Activity (spec 005).
        (webView.parent as? ViewGroup)?.removeView(webView)
        (webView.context as? MutableContextWrapper)?.let { it.baseContext = it.applicationContext }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = null
        webView.destroy()
    }

    // Điểm vào của callback WebView; internal để test mô phỏng.
    fun dispatchPageStarted(url: String) {
        if (isClosed) return
        val security = if (sslErrorInLoad) SecurityState.Insecure else SecurityState.Unknown
        _state.update { it.copy(url = url, isLoading = true, securityState = security) }
    }

    fun dispatchPageFinished(url: String) {
        if (isClosed) return
        // Cache-first chỉ áp dụng cho chính điều hướng lịch sử; điều hướng sau đó (vd. bấm link) dùng mặc định.
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        val security = securityStateOnFinish(url, sslErrorInLoad)
        sslErrorInLoad = false
        _state.update { it.copy(url = url, isLoading = false, progress = 100, securityState = security) }
        syncHistory()
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

    private fun syncHistory() {
        if (isClosed) return
        _state.update { it.copy(canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward()) }
    }

    private companion object {
        const val STATE_MAGIC = 0x50585753 // "PXWS"
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
