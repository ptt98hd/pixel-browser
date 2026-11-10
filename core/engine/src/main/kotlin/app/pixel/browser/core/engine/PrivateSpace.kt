package app.pixel.browser.core.engine

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

/**
 * Cổng tới WebView `Profile` API (androidx.webkit, spec 016). Tách khỏi `androidx.webkit` để [PrivateSpace]
 * test được trên JVM với `FakeProfileGateway`.
 */
interface ProfileGateway {
    /** `WebViewFeature.MULTI_PROFILE` được hỗ trợ bởi WebView provider hiện tại. */
    val isSupported: Boolean

    fun listProfileNames(): List<String>

    /** `WebViewCompat.setProfile`; ném nếu không áp được. */
    fun apply(
        webView: WebView,
        profileName: String,
    )

    /** true: đã xóa/không tồn tại; false: profile còn đang dùng. */
    fun delete(profileName: String): Boolean
}

/** Cài đặt thật dựa trên `androidx.webkit` (spec 016). */
object AndroidxProfileGateway : ProfileGateway {
    /** Cố định trong cả process: provider WebView chỉ đổi khi cập nhật (process bị kill). */
    override val isSupported: Boolean by lazy {
        try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (_: RuntimeException) {
            false // không truy vấn được provider → coi như không hỗ trợ (fail-closed)
        }
    }

    override fun listProfileNames(): List<String> =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().allProfileNames
        } else {
            emptyList()
        }

    override fun apply(
        webView: WebView,
        profileName: String,
    ) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getOrCreateProfile(profileName)
            WebViewCompat.setProfile(webView, profileName)
        } else {
            throw UnsupportedOperationException("MULTI_PROFILE is not supported")
        }
    }

    override fun delete(profileName: String): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return true
        val store = ProfileStore.getInstance()
        return if (profileName in store.allProfileNames) {
            try {
                store.deleteProfile(profileName)
            } catch (_: IllegalStateException) {
                false // còn WebView dùng profile này
            }
        } else {
            true
        }
    }
}

/** Không thể tạo tab ẩn danh cách ly (thiếu `MULTI_PROFILE` hoặc không áp được profile) – fail-closed. */
class PrivateUnavailableException(
    message: String,
) : IllegalStateException(message)

fun interface RetryScheduler {
    fun schedule(
        delayMs: Long,
        block: () -> Unit,
    )
}

/** Lên lịch trên main looper. */
object MainHandlerScheduler : RetryScheduler {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun schedule(
        delayMs: Long,
        block: () -> Unit,
    ) {
        handler.postDelayed(block, delayMs)
    }
}

/** Không gian ẩn danh: một profile WebView lười, tên ngẫu nhiên không tái dùng (spec 016). */
class PrivateSpace(
    private val gateway: ProfileGateway,
    private val newName: () -> String = { PROFILE_PREFIX + UUID.randomUUID().toString().replace("-", "") },
    private val scheduler: RetryScheduler = MainHandlerScheduler,
    private val backoffMs: List<Long> = listOf(250L, 1000L, 5000L),
) {
    private val lock = Any()
    private var current: String? = null
    private var swept = false
    private val pending = linkedSetOf<String>()

    val isAvailable: Boolean get() = gateway.isSupported

    /** null nếu không hỗ trợ; lần đầu trong process quét `private_*` cũ. */
    fun acquireProfile(): String? {
        if (!gateway.isSupported) return null
        synchronized(lock) {
            retryPending()
            sweep()
            return current ?: newName().also { current = it }
        }
    }

    /**
     * Quét và xóa mọi profile `private_*` còn sót (app bị kill khi đang có tab ẩn danh). Gọi lười sau khung hình đầu
     * khi rảnh, không cần mở tab ẩn danh; chỉ chạy một lần thành công mỗi process (liệt kê lỗi → lần sau thử lại).
     */
    fun sweepStale() {
        if (!gateway.isSupported) return
        synchronized(lock) { sweep() }
    }

    /** Xóa profile hiện tại (thử lại theo backoff); lần acquire sau cho tên mới. */
    fun wipe() {
        val name =
            synchronized(lock) {
                current.also { current = null }
            } ?: return
        attempt(name, 0)
    }

    val pendingWipeCount: Int get() = synchronized(lock) { pending.size }

    /** Chỉ đánh dấu đã quét sau khi liệt kê được tên profile; lỗi → giữ nguyên để thử lại. */
    private fun sweep() {
        if (swept) return
        val stale = runCatching { gateway.listProfileNames() }.getOrNull() ?: return
        swept = true
        for (name in stale) {
            if (name.startsWith(PROFILE_PREFIX) && name != current && !tryDelete(name)) pending += name
        }
    }

    private fun retryPending() {
        for (name in pending.toList()) if (tryDelete(name)) pending -= name
    }

    private fun attempt(
        name: String,
        retry: Int,
    ) {
        if (tryDelete(name)) return
        if (retry < backoffMs.size) {
            scheduler.schedule(backoffMs[retry]) { attempt(name, retry + 1) }
        } else {
            synchronized(lock) { pending += name }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun tryDelete(name: String): Boolean =
        try {
            gateway.delete(name)
        } catch (_: RuntimeException) {
            false
        }

    private companion object {
        const val PROFILE_PREFIX = "private_"
    }
}

/** Thêm `IME_FLAG_NO_PERSONALIZED_LEARNING` vào [imeOptions] (spec 016 AC7). */
fun privateImeOptions(imeOptions: Int): Int = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING

/** Thêm `TYPE_TEXT_FLAG_NO_SUGGESTIONS` (tắt gợi ý/tự sửa) vào [inputType] của ô văn bản; lớp khác giữ nguyên. */
fun privateInputType(inputType: Int): Int =
    if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
        inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    } else {
        inputType
    }

/** Áp cả hai thay đổi riêng tư lên [info] (ô nhập của app ở tab ẩn danh; spec 016 review). */
fun applyPrivateKeyboard(info: EditorInfo) {
    info.imeOptions = privateImeOptions(info.imeOptions)
    info.inputType = privateInputType(info.inputType)
}
