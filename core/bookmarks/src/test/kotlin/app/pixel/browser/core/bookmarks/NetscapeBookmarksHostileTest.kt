package app.pixel.browser.core.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 013 AC14 – đầu vào thù địch; mỗi test có timeout 5 giây. */
class NetscapeBookmarksHostileTest {
    private val prefix = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
    private val ok = "<DT><A HREF=\"https://ok.com/\">ok</A>\n"

    private fun parse(s: String) = NetscapeBookmarks.parse(s.toByteArray(Charsets.UTF_8))

    private fun depth(f: ImportedFolder): Int = if (f.folders.isEmpty()) 0 else 1 + f.folders.maxOf { depth(it) }

    private fun count(f: ImportedFolder): Int = f.bookmarks.size + f.folders.sumOf { 1 + count(it) }

    private fun fill(
        total: Int,
        unit: String,
        head: String = "",
    ): ByteArray {
        val out = ByteArray(total)
        val headBytes = head.toByteArray()
        headBytes.copyInto(out)
        val u = unit.toByteArray()
        var i = headBytes.size
        while (i < total) {
            out[i] = u[(i - headBytes.size) % u.size]
            i++
        }
        return out
    }

    // (a)
    @Test(timeout = 5000)
    fun a_oneByteOverLimit_isTooLarge() {
        val bytes = fill(BookmarkLimits.MAX_IMPORT_BYTES + 1, " ", prefix + ok)
        assertEquals(NetscapeParse.TooLarge, NetscapeBookmarks.parse(bytes))
    }

    @Test(timeout = 5000)
    fun a_exactlyAtLimit_isProcessedNormally() {
        val bytes = fill(BookmarkLimits.MAX_IMPORT_BYTES, " ", prefix + ok)
        val r = NetscapeBookmarks.parse(bytes)
        assertTrue("was $r", r is NetscapeParse.Parsed)
        assertEquals(1, (r as NetscapeParse.Parsed).root.bookmarks.size)
    }

    // (b)
    @Test(timeout = 5000)
    fun b_hundredThousandNestedDl_noStackOverflow_truncated_depthCapped() {
        val sb = StringBuilder(prefix)
        repeat(100_000) { sb.append("<DL>") }
        sb.append(ok)
        val r = parse(sb.toString())
        assertTrue("was $r", r is NetscapeParse.Parsed)
        r as NetscapeParse.Parsed
        assertTrue(r.truncated)
        assertTrue("depth ${depth(r.root)}", depth(r.root) <= BookmarkLimits.MAX_DEPTH)
    }

    @Test(timeout = 5000)
    fun b_deeplyNestedFoldersWithH3_depthCapped() {
        val sb = StringBuilder(prefix)
        repeat(5_000) { sb.append("<DT><H3>f$it</H3><DL><p>") }
        sb.append(ok)
        val r = parse(sb.toString()) as NetscapeParse.Parsed
        assertTrue(r.truncated)
        assertTrue(depth(r.root) <= BookmarkLimits.MAX_DEPTH)
    }

    // (c)
    @Test(timeout = 5000)
    fun c_fiftyThousandBookmarks_capsAtMaxNodes_truncated() {
        val sb = StringBuilder(prefix)
        repeat(50_000) { sb.append("<DT><A HREF=\"https://a.com/$it\">b$it</A>\n") }
        val r = parse(sb.toString()) as NetscapeParse.Parsed
        assertTrue(r.truncated)
        assertTrue("nodes ${count(r.root)}", count(r.root) <= BookmarkLimits.MAX_NODES)
        assertTrue(count(r.root) > 0)
    }

    // (d)
    @Test(timeout = 5000)
    fun d_tenMiBOfLessThan_finishesWithoutThrowing() {
        val r = NetscapeBookmarks.parse(fill(BookmarkLimits.MAX_IMPORT_BYTES, "<"))
        assertEquals(NetscapeParse.NotBookmarkFile, r)
    }

    @Test(timeout = 5000)
    fun d_tenMiBOfRepeatedAnchorOpen_finishesWithoutThrowing() {
        val r = NetscapeBookmarks.parse(fill(BookmarkLimits.MAX_IMPORT_BYTES, "<A ", "<DL>"))
        assertEquals(NetscapeParse.NotBookmarkFile, r)
    }

    @Test(timeout = 5000)
    fun d_manyUnterminatedComments_andTags_finishQuickly() {
        val r = NetscapeBookmarks.parse(fill(BookmarkLimits.MAX_IMPORT_BYTES, "<!-- <DT><A HREF=\"x"))
        assertTrue(r == NetscapeParse.NotBookmarkFile || r is NetscapeParse.Parsed)
    }

    // (e)
    @Test(timeout = 5000)
    fun e_fiveMiBUnquotedUnterminatedAttribute_isDropped_withoutThrowing() {
        val huge = "a".repeat(5 * 1024 * 1024)
        val r = parse(prefix + ok + "<DT><A HREF=$huge") as NetscapeParse.Parsed
        assertEquals(listOf("https://ok.com/"), r.root.bookmarks.map { it.url })
    }

    @Test(timeout = 5000)
    fun e_fiveMiBQuotedAttributeValue_isDroppedNotKept() {
        val huge = "a".repeat(5 * 1024 * 1024)
        val r = parse(prefix + ok + "<DT><A HREF=\"https://a.com/$huge\">x</A>") as NetscapeParse.Parsed
        assertEquals(listOf("https://ok.com/"), r.root.bookmarks.map { it.url })
    }

    // (f)
    @Test(timeout = 5000)
    fun f_notBookmarkFiles_areNotBookmarkFile() {
        assertEquals(NetscapeParse.NotBookmarkFile, parse(""))
        assertEquals(
            NetscapeParse.NotBookmarkFile,
            parse("<html><body><p>hello <a href=\"https://a.com/\">a</a></p></body></html>"),
        )
        assertEquals(NetscapeParse.NotBookmarkFile, parse("<DL><p></DL><p>"))
        assertEquals(NetscapeParse.NotBookmarkFile, parse("<DL><DT><A HREF=\"javascript:alert(1)\">x</A></DL>"))
        val png =
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D) +
                ByteArray(2000) { it.toByte() }
        assertEquals(NetscapeParse.NotBookmarkFile, NetscapeBookmarks.parse(png))
    }

    // (g)
    @Test(timeout = 5000)
    fun g_externalEntity_isNotResolved() {
        val html =
            "<!DOCTYPE NETSCAPE-Bookmark-file-1 [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n" +
                "<?xml version=\"1.0\"?>\n<DL><DT><A HREF=\"https://a.com/\">&xxe;</A></DL>"
        val r = parse(html) as NetscapeParse.Parsed
        val title =
            r.root.bookmarks
                .single()
                .title
        assertFalse(title, title.contains("root:"))
    }

    @Test(timeout = 5000)
    fun g_billionLaughs_isNotExpanded() {
        val lol = "lol".repeat(1000)
        val html =
            "<!DOCTYPE NETSCAPE-Bookmark-file-1 [<!ENTITY a \"$lol\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">" +
                "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">]>\n<DL><DT><A HREF=\"https://a.com/\">&c;</A></DL>"
        val r = parse(html) as NetscapeParse.Parsed
        val title =
            r.root.bookmarks
                .single()
                .title
        assertFalse(title, title.contains("lollol"))
        assertTrue(title.length <= 200)
    }

    // (h)
    @Test(timeout = 5000)
    fun h_overlongTitle_isTruncatedTo200_overlongUrl_isSkipped() {
        val longUrl = "https://a.com/" + "a".repeat(BookmarkLimits.MAX_URL)
        val html = prefix + "<DT><A HREF=\"https://t.com/\">${"x".repeat(1000)}</A>\n<DT><A HREF=\"$longUrl\">u</A>\n"
        val r = parse(html) as NetscapeParse.Parsed
        assertEquals(1, r.root.bookmarks.size)
        assertEquals(
            BookmarkLimits.MAX_TITLE,
            r.root.bookmarks
                .single()
                .title.length,
        )
        assertEquals(1, r.skipped)
    }

    @Test(timeout = 5000)
    fun h_overlongFolderTitle_isTruncated() {
        val html = prefix + "<DT><H3>${"y".repeat(1000)}</H3><DL><p>$ok</DL><p>"
        val r = parse(html) as NetscapeParse.Parsed
        assertEquals(
            BookmarkLimits.MAX_TITLE,
            r.root.folders
                .single()
                .title.length,
        )
    }

    // malformed nesting
    @Test(timeout = 5000)
    fun malformedNesting_extraClosingTags_doNotThrowOrCorruptTree() {
        val html = "</DL></DL></DL><DL>$ok</DL></DL></DL><DT><A HREF=\"https://b.com/\">b</A>"
        val r = NetscapeBookmarks.parse(html.toByteArray())
        assertTrue(r is NetscapeParse.Parsed)
        val urls =
            (r as NetscapeParse.Parsed)
                .root
                .let { root ->
                    root.bookmarks + root.folders.flatMap { it.bookmarks }
                }.map { it.url }
        assertTrue(urls.containsAll(listOf("https://ok.com/", "https://b.com/")))
    }

    @Test(timeout = 5000)
    fun malformedNesting_h3WithoutDl_andDlWithoutH3_doNotThrow() {
        val html = "<DL><DT><H3>orphan</H3>$ok<DL>$ok</DL></DL>"
        assertTrue(NetscapeBookmarks.parse(html.toByteArray()) is NetscapeParse.Parsed)
    }
}
