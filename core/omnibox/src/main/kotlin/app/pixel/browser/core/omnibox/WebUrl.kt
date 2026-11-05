package app.pixel.browser.core.omnibox

import java.net.IDN

/** Tiện ích phân tích URL web thuần Kotlin/JVM dùng chung cho omnibox (spec 004). */
internal object WebUrl {
    private const val MAX_HOST_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
    private const val MAX_PORT = 65_535
    private val WEB_SCHEMES = setOf("http", "https")

    /** Host đã chuẩn hóa (lowercase, Punycode) và loại host. */
    data class Host(
        val value: String,
        val isLocal: Boolean,
    )

    /** Authority (`host[:port]`) của URL `http`/`https`; `null` nếu không phải URL web dạng `scheme://`. */
    fun authorityOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0 || url.substring(0, schemeEnd).lowercase() !in WEB_SCHEMES) return null
        val rest = url.substring(schemeEnd + 3)
        return rest.substring(0, endOfAuthority(rest))
    }

    private const val AUTHORITY_TERMINATORS = "/?#\\"

    /**
     * Vị trí kết thúc authority: ký tự `/`, `?`, `#`, `\` đầu tiên hoặc cuối chuỗi.
     * `\` được coi như `/` giống trình duyệt (WHATWG URL) để `evil.com\@google.com` không đánh lừa.
     */
    fun endOfAuthority(s: String): Int {
        val end = s.indexOfFirst { it in AUTHORITY_TERMINATORS }
        return if (end < 0) s.length else end
    }

    /**
     * Chuẩn hóa authority `host[:port]` thành `host[:port]` hợp lệ; `null` nếu không hợp lệ.
     * [strict] = không có scheme: yêu cầu domain có TLD hợp lệ (hoặc localhost/IP literal).
     */
    fun normalizeAuthority(
        authority: String,
        strict: Boolean,
    ): Pair<Host, String>? {
        if (authority.isEmpty() || '@' in authority) return null
        val (rawHost, port) =
            when {
                authority.startsWith('[') -> {
                    val close = authority.indexOf(']')
                    if (close < 0) return null
                    val inner = authority.substring(1, close)
                    if (!isIpv6(inner)) return null
                    val after = authority.substring(close + 1)
                    val port =
                        when {
                            after.isEmpty() -> null
                            after.startsWith(':') -> after.substring(1)
                            else -> return null
                        }
                    "[${inner.lowercase()}]" to port
                }

                authority.count { it == ':' } > 1 -> {
                    if (!isIpv6(authority)) return null
                    "[${authority.lowercase()}]" to null
                }

                ':' in authority -> {
                    authority.substringBefore(':') to authority.substringAfter(':')
                }

                else -> {
                    authority to null
                }
            }
        if (port != null && !isValidPort(port)) return null
        val host = normalizeHost(rawHost, strict) ?: return null
        return host to (if (port != null) "${host.value}:$port" else host.value)
    }

    fun isValidPort(port: String): Boolean =
        port.length in 1..5 && port.all { it in '0'..'9' } && port.toInt() in 1..MAX_PORT

    private fun normalizeHost(
        raw: String,
        strict: Boolean,
    ): Host? {
        if (raw.startsWith('[')) return Host(raw, isLocal = true)
        if (raw.isEmpty() || raw.length > MAX_HOST_LENGTH + 1) return null
        val lower = raw.lowercase()
        if (lower == "localhost") return Host(lower, isLocal = true)
        if (lower.all { it.isDigit() || it == '.' }) {
            return if (isIpv4(lower)) Host(lower, isLocal = true) else null
        }
        val trailingDot = lower.endsWith('.')
        val body = lower.removeSuffix(".")
        if (body.isEmpty()) return null
        // Không round-trip (ß, variation selector, ...) → host mơ hồ, không điều hướng.
        val ascii =
            try {
                IDN.toASCII(body).lowercase().takeIf { IDN.toUnicode(it) == body }
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null
        if (ascii.length > MAX_HOST_LENGTH) return null
        val labels = ascii.split('.')
        if (!labels.all(::isValidLabel)) return null
        if (strict && (labels.size < 2 || !isValidTld(labels.last()))) return null
        return Host(if (trailingDot) "$ascii." else ascii, isLocal = false)
    }

    private fun isValidLabel(label: String): Boolean =
        label.length in 1..MAX_LABEL_LENGTH &&
            label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } &&
            !label.startsWith('-') &&
            !label.endsWith('-')

    private fun isValidTld(tld: String): Boolean = tld.length >= 2 && !tld.all { it.isDigit() }

    fun isIpv4(s: String): Boolean {
        val parts = s.split('.')
        return parts.size == 4 &&
            parts.all { p ->
                p.length in 1..3 && p.all { it in '0'..'9' } && (p == "0" || !p.startsWith('0')) && p.toInt() <= 255
            }
    }

    fun isIpv6(s: String): Boolean {
        if (s.isEmpty() || s.count { it == ':' } < 2) return false
        val halves = s.split("::")
        if (halves.size > 2) return false
        val groups = halves.flatMap { half -> if (half.isEmpty()) emptyList() else half.split(':') }
        var count = 0
        groups.forEachIndexed { i, g ->
            count +=
                when {
                    i == groups.lastIndex && '.' in g -> if (isIpv4(g)) 2 else return false
                    g.length in 1..4 && g.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } -> 1
                    else -> return false
                }
        }
        return if (halves.size == 2) count < 8 else count == 8
    }
}
