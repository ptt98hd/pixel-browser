package app.pixel.browser.core.engine

import android.graphics.Bitmap
import android.view.View
import kotlinx.coroutines.flow.SharedFlow
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
    /** Trang đang hiển thị video toàn màn hình (spec 007 AC8). */
    val isFullscreen: Boolean = false,
    /** scrollY của root == 0; true khi bắt đầu tải trang mới (spec 008 AC1). */
    val isAtTop: Boolean = true,
)

/** Một phiên duyệt web (tương ứng một tab) che engine bên dưới để logic test được trên JVM. */
interface EngineSession {
    val state: StateFlow<EngineState>

    /** View hiển thị nội dung trang (spec 005); null nếu engine không có view (Fake) hoặc đã [close]. */
    val view: View?

    fun loadUrl(url: String)

    fun goBack()

    fun goForward()

    fun reload()

    fun stopLoading()

    /** Tạm dừng engine khi không hiển thị (tab nền, Activity pause). No-op sau [close]. */
    fun onPause()

    /** Tiếp tục engine khi hiển thị lại. No-op sau [close]. */
    fun onResume()

    /** Cuộn trang lên đầu (spec 005, A07). No-op sau [close]. */
    fun scrollToTop()

    /** Cuộn trang xuống cuối (spec 005, A07). No-op sau [close]. */
    fun scrollToBottom()

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

    /** Hàng đợi FIFO các request chưa trả lời (spec 006). */
    val requests: StateFlow<List<EngineRequest>>

    /**
     * Trả lời request; false nếu id không tồn tại/đã trả lời/response không hợp lệ với loại request
     * (request giữ nguyên) – spec 006.
     */
    fun respond(
        requestId: Long,
        response: EngineResponse,
    ): Boolean

    /** View video toàn màn hình (WebView: view của `onShowCustomView`); Fake và khi không fullscreen: null. */
    val fullscreenView: View?

    /** Yêu cầu engine thoát fullscreen (spec 007 AC8). */
    fun exitFullscreen()

    /** Favicon của trang hiện tại; null khi chưa có. Reset về null khi bắt đầu tải trang main-frame mới (spec 009). */
    val favicon: StateFlow<Bitmap?>

    /** Phát mỗi lần root scroller bị kéo quá đỉnh (overscroll dọc ở scrollY == 0). Không replay (spec 008 AC2). */
    val topOverscroll: SharedFlow<Unit>
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

/**
 * [SecurityState] khi trang [url] tải xong (spec 005): lỗi SSL trong lần tải → [SecurityState.Insecure];
 * `https://` → [SecurityState.Secure]; `http://` → [SecurityState.Insecure]; còn lại → [SecurityState.Unknown].
 */
fun securityStateOnFinish(
    url: String,
    hadSslError: Boolean,
): SecurityState {
    val scheme = url.substringBefore("://", "").lowercase()
    return when {
        hadSslError -> SecurityState.Insecure
        scheme == "https" -> SecurityState.Secure
        scheme == "http" -> SecurityState.Insecure
        else -> SecurityState.Unknown
    }
}

fun clampProgress(progress: Int): Int = progress.coerceIn(0, 100)

/**
 * Áp dụng [NavigationPolicy] cho điều hướng do trang khởi tạo (spec 006 AC5): `External` ở main frame
 * phát [EngineRequest.ExternalApp]. Trả true nếu engine phải chặn điều hướng.
 */
fun EngineRequestQueue.handleNavigation(
    currentUrl: String,
    url: String,
    isMainFrame: Boolean,
    hasGesture: Boolean,
    isRedirect: Boolean,
): Boolean =
    when (NavigationPolicy.decide(url, isMainFrame)) {
        NavigationDecision.LoadInEngine -> {
            false
        }

        NavigationDecision.Block -> {
            true
        }

        NavigationDecision.External -> {
            enqueue(
                build = { id -> EngineRequest.ExternalApp(id, originOf(currentUrl), url, hasGesture, isRedirect) },
                // Engine không cần làm gì thêm khi request được trả lời: UI tự mở app ngoài.
                onResponse = { _, response -> response is EngineResponse.Dismiss },
            )
            true
        }
    }
