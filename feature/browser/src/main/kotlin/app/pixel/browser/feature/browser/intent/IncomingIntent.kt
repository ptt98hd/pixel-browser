package app.pixel.browser.feature.browser.intent

import app.pixel.browser.core.engine.schemeOf
import app.pixel.browser.core.omnibox.OmniboxClassifier
import app.pixel.browser.core.omnibox.OmniboxResult
import app.pixel.browser.core.omnibox.SearchEngine

/** Dữ liệu đã đọc an toàn từ một Intent vào (spec 006). Lớp thuần JVM. */
data class IncomingIntent(
    val action: String?,
    val dataString: String?,
    val type: String?,
    /** `Intent.EXTRA_TEXT` */
    val extraText: String?,
    /** `SearchManager.QUERY` */
    val query: String?,
    /** `Intent.EXTRA_PROCESS_TEXT` (CharSequence → String) */
    val processText: String?,
    val categories: Set<String>,
    /** `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` */
    val launchedFromHistory: Boolean,
) {
    companion object
}

sealed interface IncomingRequest {
    data class OpenUrl(
        val url: String,
    ) : IncomingRequest

    data class Search(
        val query: String,
        val url: String,
    ) : IncomingRequest

    data object Resume : IncomingRequest

    data class Rejected(
        val reason: RejectReason,
    ) : IncomingRequest
}

enum class RejectReason { UnsupportedScheme, InvalidUrl, Empty }

/**
 * Chuyển [IncomingIntent] thành hành động của trình duyệt (spec 006 AC7–AC11). Mọi kiểm tra scheme nằm ở đây,
 * không dựa vào intent-filter (intent explicit vượt qua manifest).
 */
object IncomingIntentResolver {
    const val MAX_QUERY_LENGTH = 2048

    /** URL dài hơn (VIEW/SEND) bị từ chối `InvalidUrl`; văn bản SEND được cắt tới đây trước khi tìm URL. */
    const val MAX_URL_LENGTH = 8192

    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val ACTION_SEARCH = "android.intent.action.SEARCH"
    private const val ACTION_WEB_SEARCH = "android.intent.action.WEB_SEARCH"
    private const val ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"
    private const val TEXT_PLAIN = "text/plain"

    private val WEB_SCHEMES = setOf("http", "https")
    private val SHARED_URL = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}'\""

    fun resolve(
        intent: IncomingIntent,
        engine: SearchEngine,
    ): IncomingRequest {
        if (intent.launchedFromHistory) return IncomingRequest.Resume
        return when (intent.action) {
            ACTION_VIEW -> resolveUrl(intent.dataString, engine)
            ACTION_WEB_SEARCH, ACTION_SEARCH -> resolveText(intent.query, engine)
            ACTION_PROCESS_TEXT -> resolveText(intent.processText, engine)
            ACTION_SEND -> resolveShared(intent, engine)
            else -> IncomingRequest.Resume
        }
    }

    /** URL tường minh (VIEW): chỉ http/https hợp lệ; không bao giờ biến thành tìm kiếm. */
    private fun resolveUrl(
        data: String?,
        engine: SearchEngine,
    ): IncomingRequest {
        val url = data?.trim()
        if (url.isNullOrEmpty()) return IncomingRequest.Rejected(RejectReason.Empty)
        if (url.length > MAX_URL_LENGTH) return IncomingRequest.Rejected(RejectReason.InvalidUrl)
        if (schemeOf(url) !in WEB_SCHEMES) return IncomingRequest.Rejected(RejectReason.UnsupportedScheme)
        val result = OmniboxClassifier.classify(url, engine)
        return if (result is OmniboxResult.Navigate && isWebUrl(result.url)) {
            IncomingRequest.OpenUrl(result.url)
        } else {
            IncomingRequest.Rejected(RejectReason.InvalidUrl)
        }
    }

    /** Văn bản tự do (WEB_SEARCH/SEARCH/PROCESS_TEXT/SEND): phân loại như omnibox. */
    private fun resolveText(
        text: String?,
        engine: SearchEngine,
    ): IncomingRequest {
        if (text.isNullOrBlank()) return IncomingRequest.Rejected(RejectReason.Empty)
        return when (val result = OmniboxClassifier.classify(truncate(text, MAX_QUERY_LENGTH), engine)) {
            OmniboxResult.Empty -> {
                IncomingRequest.Rejected(RejectReason.Empty)
            }

            is OmniboxResult.Blocked -> {
                IncomingRequest.Rejected(RejectReason.UnsupportedScheme)
            }

            is OmniboxResult.Navigate -> {
                if (isWebUrl(result.url)) {
                    IncomingRequest.OpenUrl(result.url)
                } else {
                    IncomingRequest.Rejected(RejectReason.UnsupportedScheme)
                }
            }

            is OmniboxResult.Search -> {
                IncomingRequest.Search(result.query, result.url)
            }
        }
    }

    /** SEND text/plain: URL http(s) đầu tiên trong văn bản, nếu không có thì xử lý như văn bản tự do. */
    private fun resolveShared(
        intent: IncomingIntent,
        engine: SearchEngine,
    ): IncomingRequest {
        val mime =
            intent.type
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
        if (mime != TEXT_PLAIN) return IncomingRequest.Rejected(RejectReason.Empty)
        val full = intent.extraText
        if (full.isNullOrBlank()) return IncomingRequest.Rejected(RejectReason.Empty)
        // Cắt trước khi chạy regex; đủ dài để URL vượt giới hạn vẫn bị nhận ra (> MAX_URL_LENGTH).
        val text = truncate(full, MAX_URL_LENGTH + 1)
        val match = SHARED_URL.find(text)
        if (match != null) {
            // URL chạm điểm cắt có thể đã bị cắt thành URL khác → từ chối thay vì mở.
            val cut = text.length < full.length && match.range.last == text.lastIndex
            val candidate = match.value.trimEnd { it in TRAILING_PUNCTUATION }
            if (cut || candidate.length > MAX_URL_LENGTH) return IncomingRequest.Rejected(RejectReason.InvalidUrl)
            val opened = resolveUrl(candidate, engine)
            if (opened is IncomingRequest.OpenUrl) return opened
        }
        return resolveText(text, engine)
    }

    /** Cắt còn [max] ký tự, không để lại nửa cặp surrogate. */
    private fun truncate(
        text: String,
        max: Int,
    ): String {
        if (text.length <= max) return text
        var end = max
        if (text[end - 1].isHighSurrogate()) end--
        return text.substring(0, end)
    }

    private fun isWebUrl(url: String): Boolean = schemeOf(url) in WEB_SCHEMES
}
