package app.pixel.browser.core.engine

import kotlinx.coroutines.flow.StateFlow

enum class SecurityState { Unknown, Secure, Insecure }

data class EngineState(
    val url: String = "",
    val title: String = "",
    val isLoading: Boolean = false,
    /** 0..100 */
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val securityState: SecurityState = SecurityState.Unknown,
    val error: String? = null,
)

/** Một phiên duyệt web (tương ứng một tab) che engine bên dưới để logic test được trên JVM. */
interface EngineSession {
    val state: StateFlow<EngineState>

    fun loadUrl(url: String)

    fun goBack()

    fun goForward()

    fun reload()

    fun stopLoading()

    /** Giải phóng engine. Mọi lời gọi sau đó là no-op. */
    fun close()
}

fun isValidWebUrl(url: String): Boolean {
    val u = url.trim()
    if (u.any { it.isWhitespace() }) return false
    val scheme = u.substringBefore("://", "").lowercase()
    val rest = u.substringAfter("://", "")
    return (scheme == "http" || scheme == "https") && rest.isNotEmpty() && !rest.startsWith("/")
}

fun clampProgress(progress: Int): Int = progress.coerceIn(0, 100)
