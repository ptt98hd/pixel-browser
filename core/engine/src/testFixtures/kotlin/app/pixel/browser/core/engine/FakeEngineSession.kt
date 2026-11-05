package app.pixel.browser.core.engine

import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
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
        _state.update {
            it.copy(url = url, isLoading = true, progress = 0, error = null, securityState = SecurityState.Unknown)
        }
    }

    private enum class Navigation { NewPage, Back, Forward, Reload }

    private var pendingNavigation = Navigation.NewPage

    override fun goBack() {
        if (closed || index <= 0) return
        pendingNavigation = Navigation.Back
        sslErrorInLoad = false
        cacheMode = CacheMode.CacheElseNetwork
        _state.update { it.copy(url = history[index - 1], isLoading = true, progress = 0) }
    }

    override fun goForward() {
        if (closed || index >= history.lastIndex) return
        pendingNavigation = Navigation.Forward
        sslErrorInLoad = false
        cacheMode = CacheMode.CacheElseNetwork
        _state.update { it.copy(url = history[index + 1], isLoading = true, progress = 0) }
    }

    override fun reload() {
        if (closed) return
        pendingNavigation = Navigation.Reload
        cacheMode = CacheMode.Default
        sslErrorInLoad = false
        _state.update { it.copy(isLoading = true, progress = 0) }
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
        val security = if (sslErrorInLoad) SecurityState.Insecure else SecurityState.Unknown
        _state.update { it.copy(url = url, isLoading = true, securityState = security) }
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
        _state.update {
            it.copy(
                url = history[index],
                title = title,
                isLoading = true,
                progress = 0,
                error = null,
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
        return requestQueue.handleNavigation(_state.value.url, url, isMainFrame, hasUserGesture, isRedirect)
    }

    private companion object {
        const val STATE_MAGIC = 0x50584653 // "PXFS"
    }
}
