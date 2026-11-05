package app.pixel.browser.core.engine

import kotlinx.coroutines.flow.StateFlow

enum class SecurityState { Unknown, Secure, Insecure }

/**
 * Chính sách cache cho một điều hướng (spec 003).
 * Điều hướng thường dùng [Default] (`LOAD_DEFAULT`); điều hướng lịch sử (back/forward/restore)
 * dùng [CacheElseNetwork] (`LOAD_CACHE_ELSE_NETWORK`).
 */
enum class CacheMode { Default, CacheElseNetwork }

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

    /**
     * Tuần tự hóa lịch sử điều hướng (và trạng thái engine nếu có) để khôi phục sau discard/process death.
     * Trả về null nếu chưa có lịch sử hoặc session đã đóng.
     */
    fun saveState(): ByteArray?

    /**
     * Khôi phục từ dữ liệu của [saveState]. Điều hướng lịch sử dùng [CacheMode.CacheElseNetwork].
     * Trả về false (và giữ nguyên trạng thái) nếu dữ liệu hỏng hoặc session đã đóng.
     */
    fun restoreState(state: ByteArray): Boolean

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

/** URL được phép làm mục hiện tại sau `restoreState`: URL web hoặc trang trống. */
fun isRestorableUrl(url: String): Boolean = url == "about:blank" || isValidWebUrl(url)

fun clampProgress(progress: Int): Int = progress.coerceIn(0, 100)
