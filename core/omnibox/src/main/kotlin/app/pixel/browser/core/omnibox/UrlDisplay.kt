package app.pixel.browser.core.omnibox

/** Định dạng URL để hiển thị trên thanh địa chỉ (spec 004). */
object UrlDisplay {
    private const val WWW = "www."

    /**
     * Host (kèm port) gọn để hiển thị: bỏ tiền tố `www.` nếu phần còn lại vẫn có dấu chấm,
     * giữ nguyên Punycode (chống homograph). `about:` → `""`; không parse được → trả nguyên chuỗi.
     */
    fun host(url: String): String {
        if (isBlankPage(url)) return ""
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return url
        val rest = url.substring(schemeEnd + 3)
        val authority = rest.substring(0, WebUrl.endOfAuthority(rest)).substringAfterLast('@')
        if (authority.isEmpty()) return url
        val hostOnly = if (authority.startsWith('[')) authority else authority.substringBefore(':')
        return if (hostOnly.startsWith(WWW, ignoreCase = true) && '.' in hostOnly.substring(WWW.length)) {
            authority.substring(WWW.length)
        } else {
            authority
        }
    }

    /** URL đầy đủ để người dùng sửa; trang trống (`about:`) → `""`. */
    fun forEditing(url: String): String = if (isBlankPage(url)) "" else url

    private fun isBlankPage(url: String): Boolean = url.isEmpty() || url.startsWith("about:", ignoreCase = true)
}
