package app.pixel.browser.core.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 013 AC11-AC13, AC15 (JVM thuần). Giả định về `Parsed.root`: là thư mục vô danh chứa nội dung của `<DL>`
 * ngoài cùng (`root.title` không được kiểm tra); `write` ghi `root.title` trong `<H1>` rồi `<DL>` của nội dung.
 */
class NetscapeBookmarksTest {
    private fun parse(html: String) = NetscapeBookmarks.parse(html.toByteArray(Charsets.UTF_8))

    private fun parsed(html: String): NetscapeParse.Parsed {
        val r = parse(html)
        assertTrue("expected Parsed but was $r", r is NetscapeParse.Parsed)
        return r as NetscapeParse.Parsed
    }

    private fun wrap(body: String) =
        "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n" +
            "<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n" +
            "<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n$body\n</DL><p>"

    private val ok = """<DT><A HREF="https://ok.com/">ok</A>"""

    private fun allBookmarks(f: ImportedFolder): List<ImportedBookmark> =
        f.bookmarks + f.folders.flatMap { allBookmarks(it) }

    // ---------- AC11 ----------

    private val chrome =
        """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <!-- This is an automatically generated file. -->
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        <DL><p>
            <DT><H3 ADD_DATE="1700000000" LAST_MODIFIED="1700000001" PERSONAL_TOOLBAR_FOLDER="true">Tin tức</H3>
            <DL><p>
                <DT><A HREF="https://a.com/" ADD_DATE="1700000000" ICON="data:image/png;base64,iVBORw0KGgo=">A</A>
                <DT><H3 ADD_DATE="1700000002">Cấp hai</H3>
                <DL><p>
                    <DT><A HREF="https://b.com/" ADD_DATE="abc">B</A>
                    <DT><H3>Cấp ba</H3>
                    <DL><p>
                        <DT><A HREF="https://c.com/">C</A>
                    </DL><p>
                </DL><p>
                <DT><A HREF="https://d.com/" ADD_DATE="1700000009">D</A>
            </DL><p>
            <DT><A HREF="https://top.com/">Top</A>
        </DL><p>
        """.trimIndent()

    @Test
    fun ac11_chromeFile_parsesTreeNamesUrlsOrderAndDates() {
        val p = parsed(chrome)
        assertEquals(0, p.skipped)
        assertFalse(p.truncated)
        assertEquals(listOf("Top"), p.root.bookmarks.map { it.title })
        val news = p.root.folders.single()
        assertEquals("Tin tức", news.title)
        assertEquals(listOf("A", "D"), news.bookmarks.map { it.title })
        assertEquals(listOf(1_700_000_000L, 1_700_000_009L), news.bookmarks.map { it.addedAtSeconds })
        val l2 = news.folders.single()
        assertEquals("Cấp hai", l2.title)
        assertNull("non-numeric ADD_DATE", l2.bookmarks.single().addedAtSeconds)
        assertNull(
            "missing ADD_DATE",
            p.root.bookmarks
                .single()
                .addedAtSeconds,
        )
        val l3 = l2.folders.single()
        assertEquals("Cấp ba", l3.title)
        assertEquals(ImportedBookmark("C", "https://c.com/", null), l3.bookmarks.single())
    }

    @Test
    fun ac11_icon_isIgnored_andNeverLeaksIntoTree() {
        val p = parsed(chrome)
        assertFalse(allBookmarks(p.root).any { it.url.startsWith("data:") || it.title.contains("base64") })
    }

    @Test
    fun ac11_lowerAndMixedCaseTags_missingCloseTags_missingFinalDl_stillParse() {
        val html =
            "<dl><dt><h3>Folder</h3>\n<dl><dt><a href=\"https://a.com/\">A</a>\n<DT><A hReF=\"https://b.com/\">B"
        val p = parsed(html)
        assertEquals(
            "Folder",
            p.root.folders
                .single()
                .title,
        )
        assertEquals(
            listOf("A", "B"),
            p.root.folders
                .single()
                .bookmarks
                .map { it.title },
        )
    }

    @Test
    fun ac11_newlinesAndWhitespaceInsideTags_stillParse() {
        val html = "<DL>\n<DT>\n<A\n   HREF = \"https://a.com/\"\n\tADD_DATE=\"5\"\n>\n A \n</A\n>\n</DL>"
        val p = parsed(html)
        val b = p.root.bookmarks.single()
        assertEquals("https://a.com/", b.url)
        assertEquals(5L, b.addedAtSeconds)
        assertEquals("A", b.title)
    }

    @Test
    fun ac11_hugeOrNegativeAddDate_becomesNull() {
        val p =
            parsed(
                wrap(
                    "<DT><A HREF=\"https://a.com/\" ADD_DATE=\"-5\">a</A>" +
                        "<DT><A HREF=\"https://b.com/\" ADD_DATE=\"99999999999999999999999\">b</A>",
                ),
            )
        assertTrue(p.root.bookmarks.all { it.addedAtSeconds == null })
    }

    @Test
    fun ac11_foldersAndBookmarksInterleaved_keepPerGroupOrder() {
        val p =
            parsed(
                wrap(
                    "<DT><A HREF=\"https://1.com/\">b1</A><DT><H3>f1</H3><DL><p></DL><p>" +
                        "<DT><A HREF=\"https://2.com/\">b2</A><DT><H3>f2</H3><DL><p></DL><p>",
                ),
            )
        assertEquals(listOf("b1", "b2"), p.root.bookmarks.map { it.title })
        assertEquals(listOf("f1", "f2"), p.root.folders.map { it.title })
    }

    // ---------- AC12 ----------

    private fun titleOf(rawTitleHtml: String): String {
        val p = parsed(wrap("""<DT><A HREF="https://a.com/">$rawTitleHtml</A>$ok"""))
        return p.root.bookmarks
            .first()
            .title
    }

    @Test
    fun ac12_namedAndNumericEntities_areDecodedInTitle() {
        assertEquals("& < > \" ' '", titleOf("&amp; &lt; &gt; &quot; &#39; &#x27;"))
        assertEquals("é😀", titleOf("&#233;&#x1F600;"))
        assertTrue(titleOf("a&nbsp;b") in setOf("a b", "a b"))
    }

    @Test
    fun ac12_entitiesInHref_areDecoded() {
        val p = parsed(wrap("""<DT><A HREF="https://a.com/?x=1&amp;y=2">a</A>"""))
        assertEquals(
            "https://a.com/?x=1&y=2",
            p.root.bookmarks
                .single()
                .url,
        )
    }

    @Test
    fun ac12_invalidEntities_doNotThrow_andYieldNoControlOrSurrogateChars() {
        listOf(
            "&#0;",
            "&#xD800;",
            "&#1114112;",
            "&unknown;",
            "&amp",
            "&#;",
            "&#x;",
            "&#99999999999999999999;",
        ).forEach { e ->
            val t = titleOf("x${e}y")
            assertTrue("title for $e = $t", t.startsWith("x") && t.endsWith("y"))
            assertFalse("NUL for $e", t.contains('\u0000'))
            assertTrue("lone surrogate for $e", t.none { Character.isSurrogate(it) })
        }
    }

    @Test
    fun ac12_nestedTagsInTitle_areStripped() {
        assertEquals("Đậm chữ", titleOf("<b>Đậm</b> chữ"))
    }

    @Test
    fun ac12_scriptStyleCommentImg_areDroppedWithContent() {
        val html =
            wrap(
                """
                <script>var x = "<DT><A HREF='https://evil.com/'>evil</A>";</script>
                <style>a { content: "<DT><A HREF='https://css.com/'>css</A>" }</style>
                <!-- <DT><A HREF="https://hidden.com/">hidden</A> -->
                <DT><A HREF="https://a.com/">Hi<script>alert(1)</script><style>.x{}</style><img onerror=alert(2) src=x>there</A>
                """.trimIndent(),
            )
        val p = parsed(html)
        val all = allBookmarks(p.root)
        assertEquals(listOf("https://a.com/"), all.map { it.url })
        val title = all.single().title
        listOf("alert", "onerror", ".x{}", "var x").forEach { assertFalse("title has $it: $title", title.contains(it)) }
        assertTrue(title.startsWith("Hi") && title.endsWith("there"))
    }

    @Test
    fun ac12_blankTitle_becomesHost() {
        val p = parsed(wrap("""<DT><A HREF="https://www.blank.com/x">   </A>"""))
        assertEquals(
            "blank.com",
            p.root.bookmarks
                .single()
                .title,
        )
    }

    @Test
    fun ac12_invalidUtf8_isReplaced_notThrown() {
        val prefix = "<DL><DT><A HREF=\"https://a.com/\">bad".toByteArray()
        val suffix = "</A></DL>".toByteArray()
        val bytes = prefix + byteArrayOf(0xFF.toByte(), 0xC3.toByte(), 0x28) + suffix
        val r = NetscapeBookmarks.parse(bytes) as NetscapeParse.Parsed
        assertTrue(
            r.root.bookmarks
                .single()
                .title
                .contains('�'),
        )
    }

    // ---------- AC13 ----------

    private fun skippedFor(href: String): Pair<Int, Int> {
        val p = parsed(wrap("""<DT><A HREF="$href">bad</A>$ok"""))
        return p.skipped to allBookmarks(p.root).size
    }

    @Test
    fun ac13_dangerousOrInvalidHrefs_areSkippedAndCounted() {
        val hrefs =
            listOf(
                "javascript:alert(1)",
                "JaVaScRiPt:alert(1)",
                " \tjavascript:alert(1)",
                "java\nscript:alert(1)",
                "java\u0000script:alert(1)",
                "data:text/html,<b>x</b>",
                "file:///etc/passwd",
                "content://x/y",
                "vbscript:msgbox(1)",
                "intent://x#Intent;end",
                "about:blank",
                "ftp://a.com/",
                "//a.com",
                "https:///x",
                "",
                "https://a.com/" + "a".repeat(2100),
                "https://a.com/a b",
            )
        hrefs.forEach { href ->
            val (skipped, kept) = skippedFor(href)
            assertEquals("skipped for [$href]", 1, skipped)
            assertEquals("kept for [$href]", 1, kept)
        }
    }

    @Test
    fun ac13_missingHref_isSkipped() {
        val p = parsed(wrap("""<DT><A NAME="x">no href</A>$ok"""))
        assertEquals(1, p.skipped)
        assertEquals(1, allBookmarks(p.root).size)
    }

    @Test
    fun ac13_httpAndHttps_areKept_withAnyQuoteStyle() {
        val p =
            parsed(
                wrap(
                    "<DT><A HREF=\"http://a.com/\">a</A><DT><A HREF='https://b.com/'>b</A>" +
                        "<DT><A HREF=https://c.com/ ADD_DATE=1>c</A>",
                ),
            )
        assertEquals(listOf("http://a.com/", "https://b.com/", "https://c.com/"), p.root.bookmarks.map { it.url })
        assertEquals(0, p.skipped)
    }

    @Test
    fun ac13_duplicateAttribute_firstWins() {
        val p = parsed(wrap("""<DT><A HREF="https://first.com/" HREF="https://second.com/">x</A>"""))
        assertEquals(
            "https://first.com/",
            p.root.bookmarks
                .single()
                .url,
        )
    }

    // ---------- AC15 ----------

    private fun sampleTree() =
        ImportedFolder(
            "Dấu trang",
            listOf(
                ImportedFolder(
                    "Tin tức 📰",
                    listOf(ImportedFolder("Rỗng", emptyList(), emptyList())),
                    listOf(ImportedBookmark("Báo \"mới\" & <hay>", "https://a.com/?x=1&y=\"2\"", 1_700_000_000L)),
                ),
                ImportedFolder("Đặc biệt <>&\"'", emptyList(), listOf(ImportedBookmark("s", "https://s.com/", null))),
            ),
            listOf(
                ImportedBookmark("Tiếng Việt: Ừ ự ễ", "https://vi.wikipedia.org/wiki/Việt", 1_600_000_000L),
                ImportedBookmark("😀 Cười", "https://emoji.example/😀", null),
            ),
        )

    private fun write(root: ImportedFolder) = NetscapeBookmarks.write(root)

    private fun sansRootTitle(f: ImportedFolder) = f.copy(title = "")

    @Test
    fun ac15_write_hasHeader_meta_utf8NoBom_noIcon() {
        val bytes = write(sampleTree())
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(text.contains("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">"))
        assertFalse("BOM", bytes[0] == 0xEF.toByte())
        assertFalse(text.contains("ICON"))
        assertTrue(text.contains("ADD_DATE=\"1700000000\""))
        assertEquals("one ADD_DATE per dated bookmark", 2, Regex("""<A [^>]*ADD_DATE=""").findAll(text).count())
    }

    @Test
    fun ac15_write_dlStructureBalanced_andNestedLinesIndentedDeeper() {
        val tree = sampleTree()
        val text = write(tree).toString(Charsets.UTF_8)
        val folders = 1 + 3 + 0 // root + (Tin tức, Rỗng, Đặc biệt)
        assertEquals(folders, Regex("<DL><p>").findAll(text).count())
        assertEquals(folders, Regex("</DL><p>").findAll(text).count())
        val lines = text.lines()
        val indent = { needle: String -> lines.first { it.contains(needle) }.takeWhile { it == ' ' }.length }
        assertTrue(indent("vi.wikipedia.org") < indent("a.com"))
    }

    @Test
    fun ac15_write_escapesTitleAndUrl_noRealScriptTag() {
        val tree =
            ImportedFolder(
                "R",
                listOf(ImportedFolder("<script>alert(1)</script>", emptyList(), emptyList())),
                listOf(ImportedBookmark("<script>alert(1)</script>", "https://a.com/?q=a\"b&c", null)),
            )
        val text = write(tree).toString(Charsets.UTF_8)
        assertFalse(text.contains("<script", ignoreCase = true))
        assertTrue(text.contains("&lt;script&gt;"))
        assertFalse("raw quote must not break attribute", text.contains("q=a\"b"))
    }

    @Test
    fun ac15_roundTrip_preservesNamesUrlsOrderAndDates() {
        val tree = sampleTree()
        val p = NetscapeBookmarks.parse(write(tree)) as NetscapeParse.Parsed
        assertEquals(sansRootTitle(tree), sansRootTitle(p.root))
        assertEquals(0, p.skipped)
        assertFalse(p.truncated)
    }

    @Test
    fun ac15_roundTrip_sixteenLevels() {
        var node = ImportedFolder("L16", emptyList(), listOf(ImportedBookmark("leaf", "https://leaf.com/", 1L)))
        for (i in 15 downTo 1) node = ImportedFolder("L$i", listOf(node), emptyList())
        val tree = ImportedFolder("root", listOf(node), emptyList())
        val p = NetscapeBookmarks.parse(write(tree)) as NetscapeParse.Parsed
        assertEquals(sansRootTitle(tree), sansRootTitle(p.root))
        assertFalse(p.truncated)
    }

    @Test
    fun ac15_emptyTree_isValidFile_thatParsesAsNotBookmarkFile() {
        val bytes = write(ImportedFolder("Dấu trang", emptyList(), emptyList()))
        val compact = bytes.toString(Charsets.UTF_8).filterNot { it.isWhitespace() }
        assertTrue(compact, compact.contains("<DL><p></DL><p>"))
        assertEquals(NetscapeParse.NotBookmarkFile, NetscapeBookmarks.parse(bytes))
    }

    @Test
    fun ac15_writtenFile_hasNoTagInjectionFromHostileTitles() {
        val tree =
            ImportedFolder(
                "r",
                emptyList(),
                listOf(ImportedBookmark("</A><DT><A HREF=\"https://evil.com/\">x", "https://ok.com/", null)),
            )
        val p = NetscapeBookmarks.parse(write(tree)) as NetscapeParse.Parsed
        assertEquals(listOf("https://ok.com/"), p.root.bookmarks.map { it.url })
        assertNotNull(
            p.root.bookmarks
                .single()
                .title,
        )
    }
}
