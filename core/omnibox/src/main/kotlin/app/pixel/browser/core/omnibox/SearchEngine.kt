package app.pixel.browser.core.omnibox

/**
 * Search engine với [template] chứa `%s` làm chỗ thay truy vấn (spec 004).
 *
 * @throws IllegalArgumentException nếu [template] không chứa `%s` hoặc không phải URL `http`/`https`
 *   có host hợp lệ (không userinfo, không `%s` trong authority).
 */
data class SearchEngine(
    val id: String,
    val name: String,
    val template: String,
) {
    init {
        require(PLACEHOLDER in template) { "Search template must contain $PLACEHOLDER: $template" }
        val authority = WebUrl.authorityOf(template)
        require(authority != null && WebUrl.normalizeAuthority(authority, strict = false) != null) {
            "Search template must be an http(s) URL with a valid host (no userinfo/placeholder): $template"
        }
    }

    internal companion object {
        const val PLACEHOLDER = "%s"
    }
}

/** URL tìm kiếm [query] (percent-encode UTF-8 theo RFC 3986), thay mọi `%s` trong template. */
fun SearchEngine.searchUrl(query: String): String = template.replace(SearchEngine.PLACEHOLDER, percentEncode(query))

/** Scheme + host của template, kết thúc bằng `/` (vd `https://www.google.com/`). */
val SearchEngine.homeUrl: String
    get() {
        val scheme = template.substringBefore(':').lowercase()
        return "$scheme://${WebUrl.authorityOf(template)}/"
    }

object SearchEngines {
    val Google = SearchEngine("google", "Google", "https://www.google.com/search?q=%s")
    val DuckDuckGo = SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s")
    val Brave = SearchEngine("brave", "Brave", "https://search.brave.com/search?q=%s")
    val Bing = SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s")
    val all: List<SearchEngine> = listOf(Google, DuckDuckGo, Brave, Bing)
    val Default: SearchEngine = Google
}

private const val HEX = "0123456789ABCDEF"

/** Giữ ký tự unreserved RFC 3986, còn lại percent-encode theo byte UTF-8 (hex in hoa). */
private fun percentEncode(value: String): String =
    buildString(value.length) {
        for (byte in value.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (b < 0x80 && (c.isLetterOrDigit() || c in "-._~")) {
                append(c)
            } else {
                append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
    }
