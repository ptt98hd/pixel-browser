package app.pixel.browser.core.engine

import java.net.IDN

/** Engine hỏi ở mỗi điều hướng main-frame: site này có xem ở chế độ máy tính không (spec 015 B05). */
fun interface DesktopSiteResolver {
    /** [siteKey] null: URL không có site (about:blank…). */
    fun isDesktop(siteKey: String?): Boolean
}

/**
 * Khóa site cho ngoại lệ desktop (spec 015): host viết thường, bỏ tiền tố `www.` đầu tiên, Punycode, bỏ cổng;
 * chỉ `http`/`https`, ngoài ra null.
 */
fun siteKeyOf(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    val scheme = url.substring(0, schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") return null
    val rest = url.substring(schemeEnd + "://".length)
    val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
    val hostPort = authority.substringAfterLast('@')
    val rawHost =
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return null
            hostPort.substring(0, close + 1)
        } else {
            hostPort.substringBefore(':')
        }
    if (rawHost.isEmpty() || rawHost.any { it.isISOControl() || it == '%' }) return null
    val host =
        if (rawHost.startsWith("[")) {
            rawHost.lowercase()
        } else {
            try {
                IDN.toASCII(rawHost.lowercase()).lowercase()
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
    return host.trimEnd('.').removePrefix("www.").takeIf { it.isNotEmpty() }
}

/** Biến User-Agent di động của WebView thành dạng Chrome desktop (spec 015 AC13). */
object DesktopUserAgent {
    private val platformBlock = Regex("""\(Linux; Android [^)]*\)""")
    private val mobileToken = Regex(""" Mobile(?=\s|$)""")
    private val versionToken = Regex("""Version/\d+(\.\d+)*\s""")

    fun derive(mobileUserAgent: String): String =
        mobileUserAgent
            .replace(platformBlock, "(X11; Linux x86_64)")
            .replace(mobileToken, "")
            .replace(versionToken, "")
}
