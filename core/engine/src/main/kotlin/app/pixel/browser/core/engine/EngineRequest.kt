package app.pixel.browser.core.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.URI
import java.net.URISyntaxException

/** Yêu cầu từ trang cần tầng UI xử lý (spec 006). Mỗi request được trả lời đúng một lần. */
sealed interface EngineRequest {
    /** Duy nhất trong một session, tăng dần từ 1. */
    val id: Long

    /** `originOf(url của frame/trang tạo request)`; "" nếu không xác định. */
    val origin: String

    data class ExternalApp(
        override val id: Long,
        override val origin: String,
        val uri: String,
        val hasUserGesture: Boolean,
        val isRedirect: Boolean,
    ) : EngineRequest
}

sealed interface EngineResponse {
    data class Dismiss(
        val suppressFurther: Boolean = false,
    ) : EngineResponse
}

/** `scheme://host[:port]` chữ thường, bỏ port mặc định; URL không phải http/https → "". */
fun originOf(url: String): String {
    val uri =
        try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return ""
        }
    val scheme = uri.scheme?.lowercase() ?: return ""
    val defaultPort =
        when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> return ""
        }
    val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return ""
    val port = uri.port
    return if (port == -1 || port == defaultPort) "$scheme://$host" else "$scheme://$host:$port"
}

private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

/** Scheme (chữ thường) của [url] theo cú pháp RFC 3986, bỏ khoảng trắng hai đầu; null nếu không có. */
fun schemeOf(url: String): String? =
    SCHEME
        .find(url.trim())
        ?.groupValues
        ?.get(1)
        ?.lowercase()

enum class NavigationDecision { LoadInEngine, Block, External }

/** Quyết định cho một điều hướng do trang khởi tạo (`shouldOverrideUrlLoading`) – spec 006 AC4. */
object NavigationPolicy {
    /** Scheme engine tự xử lý theo cài đặt an toàn của spec 001. */
    private val ENGINE_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript")

    /** Scheme truy cập dữ liệu cục bộ: không bao giờ tải, không chuyển sang app khác. */
    private val BLOCKED_SCHEMES = setOf("file", "content", "filesystem")

    fun decide(
        url: String,
        isMainFrame: Boolean,
    ): NavigationDecision {
        val scheme = schemeOf(url) ?: return NavigationDecision.Block
        return when {
            scheme in ENGINE_SCHEMES -> NavigationDecision.LoadInEngine
            scheme in BLOCKED_SCHEMES -> NavigationDecision.Block
            isMainFrame -> NavigationDecision.External
            else -> NavigationDecision.Block
        }
    }
}

/** Helper dùng chung cho WebView và Fake: cấp id, giữ danh sách, gọi callback khi trả lời. */
class EngineRequestQueue {
    private class Entry(
        val request: EngineRequest,
        val onResponse: (EngineResponse) -> Boolean,
    )

    private var nextId = 1L
    private val entries = LinkedHashMap<Long, Entry>()
    private val _requests = MutableStateFlow<List<EngineRequest>>(emptyList())
    val requests: StateFlow<List<EngineRequest>> = _requests

    fun <T : EngineRequest> enqueue(
        build: (id: Long) -> T,
        onResponse: (T, EngineResponse) -> Boolean,
    ): T {
        val request = build(nextId++)
        entries[request.id] = Entry(request) { response -> onResponse(request, response) }
        publish()
        return request
    }

    fun respond(
        requestId: Long,
        response: EngineResponse,
    ): Boolean {
        val entry = entries[requestId] ?: return false
        if (!entry.onResponse(response)) return false
        entries.remove(requestId)
        publish()
        return true
    }

    /** Engine tự hủy (vd. onPermissionRequestCanceled), không gọi onResponse. */
    fun cancel(requestId: Long) {
        if (entries.remove(requestId) != null) publish()
    }

    /** Dùng khi close(): trả Dismiss() cho mọi request còn treo. */
    fun dismissAll() {
        val pending = entries.values.toList()
        entries.clear()
        publish()
        pending.forEach { it.onResponse(EngineResponse.Dismiss()) }
    }

    private fun publish() {
        _requests.value = entries.values.map { it.request }
    }
}
