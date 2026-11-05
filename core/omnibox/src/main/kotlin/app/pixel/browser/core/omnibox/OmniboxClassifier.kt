package app.pixel.browser.core.omnibox

import app.pixel.browser.core.omnibox.OmniboxResult.Blocked
import app.pixel.browser.core.omnibox.OmniboxResult.Empty
import app.pixel.browser.core.omnibox.OmniboxResult.Navigate
import app.pixel.browser.core.omnibox.OmniboxResult.Search

/**
 * Phân loại input omnibox thành điều hướng / tìm kiếm / bị chặn (spec 004).
 *
 * Thứ tự: trim → rỗng → scheme bị chặn → whitespace bên trong → URL/Search.
 */
object OmniboxClassifier {
    private const val ABOUT_BLANK = "about:blank"
    private val DANGEROUS_SCHEMES = setOf("javascript", "file", "content", "intent", "data")
    private val WEB_SCHEMES = setOf("http", "https")
    private val SCHEME_REGEX = Regex("[A-Za-z][A-Za-z0-9+.-]*")

    fun classify(
        input: String,
        engine: SearchEngine,
    ): OmniboxResult {
        val text = normalize(input)
        if (text.isEmpty()) return Empty

        val scheme = schemeOf(text)
        val lowerScheme = scheme?.lowercase()
        when {
            lowerScheme in DANGEROUS_SCHEMES -> {
                return Blocked(text, BlockReason.DangerousScheme)
            }

            lowerScheme == "about" -> {
                return if (text.lowercase() == ABOUT_BLANK) {
                    Navigate(ABOUT_BLANK)
                } else {
                    Blocked(text, BlockReason.UnsupportedScheme)
                }
            }
        }

        val search = Search(text, engine.searchUrl(text))
        if (text.any(::isIgnorable)) return search

        val url =
            when {
                lowerScheme in WEB_SCHEMES -> withWebScheme(lowerScheme!!, text.substring(scheme!!.length + 1))

                // Có `:` sau một token giống scheme nhưng không phải port/IPv6 → scheme lạ (mailto:, foo:...).
                lowerScheme != null && !isPortSuffix(text, scheme!!.length) && !isIpv6Authority(text) -> null

                else -> withoutScheme(text)
            }
        return url?.let(::Navigate) ?: search
    }

    /** Input đã bỏ khoảng trắng/ký tự điều khiển/zero-width ở hai đầu. */
    internal fun normalize(input: String): String = input.trim(::isIgnorable)

    /** Token trước `:` đầu tiên nếu khớp cú pháp scheme RFC 3986, ngược lại `null`. */
    private fun schemeOf(text: String): String? {
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        return text.substring(0, colon).takeIf(SCHEME_REGEX::matches)
    }

    /** Phần sau `:` ở [colon] (tới `/?#`) là port số → `host:port`, không phải scheme. */
    private fun isPortSuffix(
        text: String,
        colon: Int,
    ): Boolean {
        val after = text.substring(colon + 1)
        val port = after.substring(0, WebUrl.endOfAuthority(after))
        return port.isNotEmpty() && port.all { it in '0'..'9' }
    }

    /** Authority là IPv6 không ngoặc (vd `fe80::1`, nhóm đầu có thể trông giống scheme). */
    private fun isIpv6Authority(text: String): Boolean = WebUrl.isIpv6(text.substring(0, WebUrl.endOfAuthority(text)))

    private fun withWebScheme(
        scheme: String,
        afterColon: String,
    ): String? {
        if (!afterColon.startsWith("//")) return null
        val rest = afterColon.substring(2)
        val end = WebUrl.endOfAuthority(rest)
        val (_, authority) = WebUrl.normalizeAuthority(rest.substring(0, end), strict = false) ?: return null
        return "$scheme://$authority${rest.substring(end)}"
    }

    private fun withoutScheme(text: String): String? {
        val end = WebUrl.endOfAuthority(text)
        val (host, authority) = WebUrl.normalizeAuthority(text.substring(0, end), strict = true) ?: return null
        val scheme = if (host.isLocal) "http" else "https"
        return "$scheme://$authority${text.substring(end)}"
    }

    /** Khoảng trắng, ký tự điều khiển và ký tự định dạng vô hình (zero-width...). */
    private fun isIgnorable(c: Char): Boolean =
        c.isWhitespace() || c.isISOControl() || Character.getType(c) == Character.FORMAT.toInt()
}
