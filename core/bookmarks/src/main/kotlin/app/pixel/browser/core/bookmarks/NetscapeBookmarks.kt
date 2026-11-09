package app.pixel.browser.core.bookmarks

sealed interface NetscapeParse {
    data class Parsed(
        val root: ImportedFolder,
        val skipped: Int,
        val truncated: Boolean,
    ) : NetscapeParse

    data object NotBookmarkFile : NetscapeParse

    data object TooLarge : NetscapeParse
}

/**
 * Parser/writer Netscape bookmark HTML. Kotlin thuần: không import `android.*`.
 *
 * Parser quét một lượt, tuyến tính theo kích thước đầu vào, không đệ quy, không regex và không tải/thực thi gì:
 * chỉ nhận thẻ `DL`/`DT`/`H3`/`A`, bỏ `script`/`style`/comment/`<!...>`/`<?...>` cùng nội dung, chỉ giải mã một bảng
 * thực thể cố định (không có `<!ENTITY>`), chỉ nhận URL http/https (xem [BookmarkRules.validUrl]).
 */
object NetscapeBookmarks {
    fun parse(bytes: ByteArray): NetscapeParse {
        if (bytes.size > BookmarkLimits.MAX_IMPORT_BYTES) return NetscapeParse.TooLarge
        return Parser(String(bytes, Charsets.UTF_8)).run()
    }

    fun write(root: ExportFolder): ByteArray {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file. It will be read and overwritten. -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n")
        sb.append("<H1>").append(escape(root.title)).append("</H1>\n")
        writeFolder(sb, root, 0)
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun writeFolder(
        sb: StringBuilder,
        folder: ImportedFolder,
        level: Int,
    ) {
        val pad = "    ".repeat(level)
        val inner = "    ".repeat(level + 1)
        sb.append(pad).append("<DL><p>\n")
        for (sub in folder.folders) {
            sb
                .append(inner)
                .append("<DT><H3>")
                .append(escape(sub.title))
                .append("</H3>\n")
            writeFolder(sb, sub, level + 1)
        }
        for (b in folder.bookmarks) {
            sb
                .append(inner)
                .append("<DT><A HREF=\"")
                .append(escape(b.url))
                .append('"')
            b.addedAtSeconds?.let { sb.append(" ADD_DATE=\"").append(it).append('"') }
            sb.append('>').append(escape(b.title)).append("</A>\n")
        }
        sb.append(pad).append("</DL><p>\n")
    }

    private fun escape(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (c in text) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private class FolderBuilder(
        val title: String,
    ) {
        val folders = ArrayList<FolderBuilder>()
        val bookmarks = ArrayList<ImportedBookmark>()

        // Độ sâu ≤ MAX_DEPTH nên đệ quy ở đây có giới hạn cứng.
        fun build(): ImportedFolder = ImportedFolder(title, folders.map { it.build() }, bookmarks.toList())
    }

    private class Parser(
        private val s: String,
    ) {
        private val n = s.length
        private val folders = ArrayList<FolderBuilder>().apply { add(FolderBuilder("")) }

        // Mỗi `<DL>` đang mở: true nếu đã đẩy một thư mục thật vào [folders].
        private var dlReal = BooleanArray(INITIAL_STACK)
        private var dlDepth = 0
        private var seenDl = false
        private var pendingFolder: String? = null
        private var nodes = 0
        private var totalBookmarks = 0
        private var skipped = 0
        private var truncated = false

        private var inAnchor = false
        private var anchorUrl = ""
        private var anchorDate: Long? = null
        private var inHeading = false
        private val text = StringBuilder()

        fun run(): NetscapeParse {
            if (n > 0 && s[0].code == BOM) scan(1) else scan(0)
            finishOpen()
            if (!seenDl || totalBookmarks == 0) return NetscapeParse.NotBookmarkFile
            return NetscapeParse.Parsed(folders[0].build(), skipped, truncated)
        }

        private fun scan(start: Int) {
            var i = start
            while (i < n) {
                val lt = s.indexOf('<', i)
                val textEnd = if (lt < 0) n else lt
                if (textEnd > i) appendText(i, textEnd)
                if (lt < 0) return
                i = handleMarkup(lt)
                if (i < 0) return
            }
        }

        /** Xử lý thứ bắt đầu bằng `<` tại [lt]; trả vị trí tiếp theo, hoặc -1 nếu hết dữ liệu hữu ích. */
        private fun handleMarkup(lt: Int): Int {
            if (s.startsWith("<!--", lt)) {
                val end = s.indexOf("-->", lt + 4)
                return if (end < 0) -1 else end + 3
            }
            val c1 = if (lt + 1 < n) s[lt + 1] else ' '
            if (c1 == '!' || c1 == '?') {
                val end = s.indexOf('>', lt + 2)
                return if (end < 0) -1 else end + 1
            }
            val closing = c1 == '/'
            val nameStart = if (closing) lt + 2 else lt + 1
            if (nameStart >= n || !isAsciiLetter(s[nameStart])) {
                if (collecting()) appendLiteral('<')
                return lt + 1
            }
            var nameEnd = nameStart
            while (nameEnd < n && (isAsciiLetter(s[nameEnd]) || s[nameEnd] in '0'..'9')) nameEnd++
            // Tên thẻ phải kết thúc bằng khoảng trắng, `/`, `>` hoặc hết file; ngược lại (`<a-b>`) không phải thẻ.
            if (nameEnd < n && !s[nameEnd].isWhitespace() && s[nameEnd] != '/' && s[nameEnd] != '>') {
                if (collecting()) appendLiteral('<')
                return lt + 1
            }
            val name = if (nameEnd - nameStart <= MAX_TAG_NAME) s.substring(nameStart, nameEnd).lowercase() else ""
            return if (closing) closingTag(name, nameEnd) else openingTag(name, nameEnd)
        }

        private fun closingTag(
            name: String,
            from: Int,
        ): Int {
            val end = s.indexOf('>', from)
            if (end < 0) return -1
            when (name) {
                "a" -> finishAnchor()
                "h3" -> finishHeading()
                "dl" -> closeDl()
            }
            return end + 1
        }

        private fun openingTag(
            name: String,
            from: Int,
        ): Int {
            if (name == "script" || name == "style") {
                val end = s.indexOf('>', from)
                if (end < 0) return -1
                return skipRawText(name, end + 1)
            }
            var hrefStart = -1
            var hrefEnd = -1
            var dateStart = -1
            var dateEnd = -1
            var j = from
            val wantAttrs = name == "a"
            while (true) {
                while (j < n && (s[j].isWhitespace() || s[j] == '/')) j++
                if (j >= n) return -1
                if (s[j] == '>') {
                    j++
                    break
                }
                val attrStart = j
                while (j < n && !s[j].isWhitespace() && s[j] != '=' && s[j] != '>' && s[j] != '/') j++
                if (j == attrStart) j++
                val attrEnd = j
                while (j < n && s[j].isWhitespace()) j++
                if (j < n && s[j] == '=') {
                    j++
                    while (j < n && s[j].isWhitespace()) j++
                    if (j >= n) return -1
                    val valueStart: Int
                    val valueEnd: Int
                    val q = s[j]
                    if (q == '"' || q == '\'') {
                        val close = s.indexOf(q, j + 1)
                        if (close < 0) return -1
                        valueStart = j + 1
                        valueEnd = close
                        j = close + 1
                    } else {
                        valueStart = j
                        while (j < n && !s[j].isWhitespace() && s[j] != '>') j++
                        valueEnd = j
                    }
                    if (wantAttrs) {
                        if (hrefStart < 0 && s.regionMatches(attrStart, "href", 0, 4, ignoreCase = true) &&
                            attrEnd - attrStart == 4
                        ) {
                            hrefStart = valueStart
                            hrefEnd = valueEnd
                        } else if (dateStart < 0 &&
                            attrEnd - attrStart == 8 &&
                            s.regionMatches(attrStart, "add_date", 0, 8, ignoreCase = true)
                        ) {
                            dateStart = valueStart
                            dateEnd = valueEnd
                        }
                    }
                }
            }
            when (name) {
                "dt", "dd" -> finishOpen()
                "dl" -> openDl()
                "h3" -> openHeading()
                "a" -> openAnchor(hrefStart, hrefEnd, dateStart, dateEnd)
            }
            return j
        }

        /** Bỏ nội dung `script`/`style` tới thẻ đóng tương ứng (không có → hết file). */
        private fun skipRawText(
            name: String,
            from: Int,
        ): Int {
            var k = from
            while (true) {
                val idx = s.indexOf("</", k)
                if (idx < 0) return -1
                if (s.regionMatches(idx + 2, name, 0, name.length, ignoreCase = true)) {
                    val end = s.indexOf('>', idx)
                    return if (end < 0) -1 else end + 1
                }
                k = idx + 2
            }
        }

        // ---- trạng thái cây ----

        private fun finishOpen() {
            finishAnchor()
            finishHeading()
        }

        private fun openDl() {
            finishOpen()
            val title = pendingFolder
            pendingFolder = null
            when {
                !seenDl -> {
                    seenDl = true
                    pushDl(true)
                }

                title == null -> {
                    pushDl(false)
                    if (dlDepth > BookmarkLimits.MAX_DEPTH + 1) truncated = true
                }

                folders.size - 1 >= BookmarkLimits.MAX_DEPTH || nodes >= BookmarkLimits.MAX_NODES -> {
                    truncated = true
                    pushDl(false)
                }

                else -> {
                    val folder = FolderBuilder(title)
                    folders.last().folders.add(folder)
                    folders.add(folder)
                    nodes++
                    pushDl(true)
                }
            }
        }

        private fun pushDl(real: Boolean) {
            if (dlDepth == dlReal.size) dlReal = dlReal.copyOf(dlReal.size * 2)
            dlReal[dlDepth++] = real
        }

        private fun closeDl() {
            finishOpen()
            pendingFolder = null
            if (dlDepth == 0) return
            val real = dlReal[--dlDepth]
            if (real && folders.size > 1) folders.removeAt(folders.size - 1)
        }

        private fun openHeading() {
            finishOpen()
            inHeading = true
            text.setLength(0)
        }

        private fun finishHeading() {
            if (!inHeading) return
            inHeading = false
            val clean = BookmarkRules.cleanTitle(text.toString())
            pendingFolder = clean.ifEmpty { FALLBACK_FOLDER_TITLE }
            text.setLength(0)
        }

        private fun openAnchor(
            hrefStart: Int,
            hrefEnd: Int,
            dateStart: Int,
            dateEnd: Int,
        ) {
            finishOpen()
            pendingFolder = null
            val url = if (hrefStart < 0) null else decodeHref(hrefStart, hrefEnd)
            if (url == null) {
                skipped++
                return
            }
            anchorUrl = url
            anchorDate = if (dateStart < 0) null else parseDate(dateStart, dateEnd)
            inAnchor = true
            text.setLength(0)
        }

        private fun finishAnchor() {
            if (!inAnchor) return
            inAnchor = false
            val title = BookmarkRules.normalizeTitle(text.toString(), anchorUrl)
            text.setLength(0)
            if (nodes >= BookmarkLimits.MAX_NODES) {
                truncated = true
                return
            }
            folders.last().bookmarks.add(ImportedBookmark(title, anchorUrl, anchorDate))
            nodes++
            totalBookmarks++
        }

        private fun decodeHref(
            start: Int,
            end: Int,
        ): String? {
            if (end - start > MAX_RAW_HREF) return null
            val sb = StringBuilder(end - start)
            decode(start, end, sb, collapse = false, cap = MAX_RAW_HREF)
            return BookmarkRules.validUrl(sb.toString().trim())
        }

        private fun parseDate(
            start: Int,
            end: Int,
        ): Long? {
            val raw = s.substring(start, end).trim()
            if (raw.isEmpty() || raw.length > MAX_DATE_DIGITS || !raw.all { it in '0'..'9' }) return null
            return raw.toLongOrNull()?.takeIf { it <= MAX_SECONDS }
        }

        // ---- văn bản ----

        private fun collecting() = inAnchor || inHeading

        private fun appendLiteral(c: Char) {
            if (text.length < TITLE_RAW_CAP) text.append(c)
        }

        private fun appendText(
            from: Int,
            to: Int,
        ) {
            if (!collecting()) return
            decode(from, to, text, collapse = true, cap = TITLE_RAW_CAP)
        }

        /** Giải mã thực thể trong `s[from, to)` vào [out] tới tối đa [cap] ký tự; [collapse] gộp khoảng trắng. */
        private fun decode(
            from: Int,
            to: Int,
            out: StringBuilder,
            collapse: Boolean,
            cap: Int,
        ) {
            var i = from
            while (i < to && out.length < cap) {
                val c = s[i]
                if (c == '&') {
                    val consumed = entity(i, to, out, collapse)
                    if (consumed > 0) {
                        i += consumed
                        continue
                    }
                }
                put(out, c, collapse)
                i++
            }
        }

        private fun put(
            out: StringBuilder,
            c: Char,
            collapse: Boolean,
        ) {
            if (collapse && c.isWhitespace()) {
                if (out.isNotEmpty() && out[out.length - 1] != ' ') out.append(' ')
            } else {
                out.append(c)
            }
        }

        /** Trả số ký tự đã tiêu thụ (>0) hoặc 0 nếu `&` ở [at] không phải thực thể hợp lệ (giữ nguyên chữ). */
        private fun entity(
            at: Int,
            to: Int,
            out: StringBuilder,
            collapse: Boolean,
        ): Int {
            val limit = minOf(to, at + MAX_ENTITY_LEN)
            var semi = -1
            for (k in at + 1 until limit) {
                if (s[k] == ';') {
                    semi = k
                    break
                }
            }
            if (semi < 0) return 0
            val body = s.substring(at + 1, semi)
            if (body.startsWith("#")) {
                val hex = body.length > 1 && (body[1] == 'x' || body[1] == 'X')
                val digits = if (hex) body.substring(2) else body.substring(1)
                val radix = if (hex) HEX else DEC
                if (digits.isEmpty() || digits.length > MAX_ENTITY_DIGITS ||
                    !digits.all { Character.digit(it, radix) >= 0 }
                ) {
                    return 0
                }
                val cp = digits.toLong(radix)
                if (cp == 0L || cp > MAX_CODE_POINT || cp in SURROGATE_LOW..SURROGATE_HIGH) {
                    out.appendCodePoint(REPLACEMENT)
                } else if (cp == NBSP_CODE) {
                    put(out, ' ', collapse)
                } else {
                    out.appendCodePoint(cp.toInt())
                }
                return semi - at + 1
            }
            val decoded =
                when (body) {
                    "amp" -> '&'
                    "lt" -> '<'
                    "gt" -> '>'
                    "quot" -> '"'
                    "apos" -> '\''
                    "nbsp" -> ' '
                    else -> return 0
                }
            put(out, decoded, collapse)
            return semi - at + 1
        }

        private fun isAsciiLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

        private companion object {
            const val BOM = 0xFEFF
            const val REPLACEMENT = 0xFFFD
            const val INITIAL_STACK = 64
            const val MAX_TAG_NAME = 8
            const val MAX_RAW_HREF = BookmarkLimits.MAX_URL * 6
            const val TITLE_RAW_CAP = 1024
            const val MAX_ENTITY_LEN = 12
            const val MAX_ENTITY_DIGITS = 8
            const val MAX_DATE_DIGITS = 12
            const val MAX_SECONDS = 253_402_300_799L
            const val MAX_CODE_POINT = 0x10FFFFL
            const val SURROGATE_LOW = 0xD800L
            const val SURROGATE_HIGH = 0xDFFFL
            const val NBSP_CODE = 0xA0L
            const val HEX = 16
            const val DEC = 10
            const val FALLBACK_FOLDER_TITLE = "-"
        }
    }
}
