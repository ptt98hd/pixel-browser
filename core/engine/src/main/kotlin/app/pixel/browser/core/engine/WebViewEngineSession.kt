package app.pixel.browser.core.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
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
    }

    override fun loadUrl(url: String) {
        if (isClosed) return
        if (!isValidWebUrl(url)) {
            _state.update { it.copy(error = "Invalid URL: $url", isLoading = false) }
            return
        }
        _state.update {
            it.copy(url = url, isLoading = true, progress = 0, error = null, securityState = SecurityState.Unknown)
        }
        webView.loadUrl(url)
    }

    override fun goBack() {
        if (!isClosed) webView.goBack()
    }

    override fun goForward() {
        if (!isClosed) webView.goForward()
    }

    override fun reload() {
        if (isClosed) return
        _state.update { it.copy(isLoading = true, progress = 0) }
        webView.reload()
    }

    override fun stopLoading() {
        if (isClosed) return
        webView.stopLoading()
        _state.update { it.copy(isLoading = false) }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = null
        webView.destroy()
    }

    // Điểm vào của callback WebView; internal để test mô phỏng.
    fun dispatchPageStarted(url: String) {
        if (isClosed) return
        _state.update { it.copy(url = url, isLoading = true) }
    }

    fun dispatchPageFinished(url: String) {
        if (isClosed) return
        _state.update { it.copy(url = url, isLoading = false, progress = 100) }
        syncHistory()
    }

    fun dispatchProgress(progress: Int) {
        if (isClosed) return
        _state.update { it.copy(progress = clampProgress(progress)) }
    }

    fun dispatchSslError() {
        if (isClosed) return
        _state.update { it.copy(securityState = SecurityState.Insecure) }
    }

    private fun syncHistory() {
        if (isClosed) return
        _state.update { it.copy(canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward()) }
    }
}
