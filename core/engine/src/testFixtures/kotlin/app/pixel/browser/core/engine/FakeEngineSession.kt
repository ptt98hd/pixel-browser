package app.pixel.browser.core.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Engine giả chạy trên JVM, tuân thủ cùng hợp đồng với [WebViewEngineSession]. */
class FakeEngineSession : EngineSession {
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state

    private val history = mutableListOf<String>()
    private var index = -1
    private var closed = false

    val loadedUrls: List<String> get() = history.toList()

    override fun loadUrl(url: String) {
        if (closed) return
        if (!isValidWebUrl(url)) {
            _state.update { it.copy(error = "Invalid URL: $url", isLoading = false) }
            return
        }
        // Lịch sử chỉ đổi khi trang commit (simulatePageFinished) — giống engine thật.
        pendingNavigation = Navigation.NewPage
        _state.update {
            it.copy(url = url, isLoading = true, progress = 0, error = null, securityState = SecurityState.Unknown)
        }
    }

    private enum class Navigation { NewPage, Back, Forward, Reload }

    private var pendingNavigation = Navigation.NewPage

    override fun goBack() {
        if (closed || index <= 0) return
        pendingNavigation = Navigation.Back
        _state.update { it.copy(url = history[index - 1], isLoading = true, progress = 0) }
    }

    override fun goForward() {
        if (closed || index >= history.lastIndex) return
        pendingNavigation = Navigation.Forward
        _state.update { it.copy(url = history[index + 1], isLoading = true, progress = 0) }
    }

    override fun reload() {
        if (closed) return
        pendingNavigation = Navigation.Reload
        _state.update { it.copy(isLoading = true, progress = 0) }
    }

    override fun stopLoading() {
        if (closed) return
        _state.update { it.copy(isLoading = false) }
    }

    override fun close() {
        closed = true
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
        _state.update {
            it.copy(
                url = url,
                isLoading = false,
                progress = 100,
                canGoBack = index > 0,
                canGoForward = index < history.lastIndex,
            )
        }
    }

    fun simulateSslError() {
        if (closed) return
        _state.update { it.copy(securityState = SecurityState.Insecure) }
    }

    fun simulateTitle(title: String) {
        if (closed) return
        _state.update { it.copy(title = title) }
    }
}
