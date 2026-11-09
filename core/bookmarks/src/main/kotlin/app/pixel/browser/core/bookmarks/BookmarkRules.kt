package app.pixel.browser.core.bookmarks

import app.pixel.browser.core.omnibox.UrlDisplay

object BookmarkRules {
    /** Bỏ ký tự điều khiển, cắt khoảng trắng hai đầu, cắt còn [BookmarkLimits.MAX_TITLE]; rỗng → host của [url]. */
    fun normalizeTitle(
        title: String,
        url: String,
    ): String {
        val clean = cleanTitle(title)
        return clean.ifEmpty { UrlDisplay.host(url).take(BookmarkLimits.MAX_TITLE) }
    }

    /** Như [normalizeTitle] nhưng không có dự phòng (rỗng → ""); dùng cho tên thư mục. */
    fun cleanTitle(title: String): String =
        title
            .filterNot { it.isISOControl() || it.isInvisibleFormat() }
            .trim()
            .take(BookmarkLimits.MAX_TITLE)
            .trim()

    /** Chỉ nhận URL tuyệt đối http/https có host, không khoảng trắng/ký tự điều khiển, dài ≤ 2048; trả chính nó. */
    fun validUrl(url: String): String? {
        if (url.isEmpty() || url.length > BookmarkLimits.MAX_URL) return null
        if (url.any { it.isWhitespace() || it.isISOControl() || it.isFormatChar() }) return null
        val schemeLen =
            when {
                url.startsWith("https://", ignoreCase = true) -> HTTPS_LEN
                url.startsWith("http://", ignoreCase = true) -> HTTP_LEN
                else -> return null
            }
        val first = url.getOrNull(schemeLen) ?: return null
        if (first == '/' || first == '?' || first == '#') return null
        return url
    }

    /** Khóa so trùng: scheme + authority viết thường, bỏ fragment, bỏ `/` đơn độc; giữ path/query. */
    fun urlKey(url: String): String {
        val noFragment = url.substringBefore('#')
        val schemeEnd = noFragment.indexOf("://")
        if (schemeEnd <= 0) return noFragment
        val scheme = noFragment.substring(0, schemeEnd).lowercase()
        val rest = noFragment.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd).lowercase()
        val tail = rest.substring(authorityEnd)
        val query = if ('?' in tail) "?" + tail.substringAfter('?') else ""
        var path = tail.substringBefore('?')
        if (path.isEmpty()) path = "/"
        if (path == "/" && query.isEmpty()) path = ""
        return "$scheme://$authority$path$query"
    }

    private fun Char.isFormatChar() = Character.getType(this) == Character.FORMAT.toInt()

    /** Ký tự định dạng ẩn (bidi, zero-width, BOM…) bị bỏ khỏi tiêu đề, trừ ZWNJ/ZWJ cần cho chữ viết và emoji ghép. */
    private fun Char.isInvisibleFormat() = isFormatChar() && this != ZWNJ && this != ZWJ

    private const val ZWNJ = '\u200C'
    private const val ZWJ = '\u200D'
    private const val HTTPS_LEN = 8
    private const val HTTP_LEN = 7
}
