package app.pixel.browser.core.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 013 AC1 – luật URL/tiêu đề/khóa trùng (JVM). */
class BookmarkRulesTest {
    @Test
    fun validUrl_acceptsAbsoluteHttpAndHttps_returningItself() {
        assertEquals("https://a.com/x", BookmarkRules.validUrl("https://a.com/x"))
        assertEquals("http://a.com", BookmarkRules.validUrl("http://a.com"))
    }

    @Test
    fun validUrl_doesNotAddSchemeToBareHost() {
        assertNull(BookmarkRules.validUrl("example.com"))
    }

    @Test
    fun validUrl_rejectsDangerousAndNonWebSchemes() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,hi",
            "file:///etc/passwd",
            "about:blank",
            "pixel://home",
            "ftp://a.com/",
            "content://x/y",
            "https:///x",
            "",
        ).forEach { assertNull("must reject: $it", BookmarkRules.validUrl(it)) }
    }

    @Test
    fun validUrl_rejectsWhitespaceInside() {
        assertNull(BookmarkRules.validUrl("https://a.com/a b"))
        assertNull(BookmarkRules.validUrl("https://a.com/\tb"))
    }

    @Test
    fun validUrl_rejectsOver2048_acceptsExactly2048() {
        val prefix = "https://a.com/"
        assertNull(BookmarkRules.validUrl(prefix + "a".repeat(2048 - prefix.length + 1)))
        val ok = prefix + "a".repeat(2048 - prefix.length)
        assertEquals(ok, BookmarkRules.validUrl(ok))
    }

    @Test
    fun normalizeTitle_trims() {
        assertEquals("Ví dụ", BookmarkRules.normalizeTitle("  Ví dụ  ", "https://a.com/"))
    }

    @Test
    fun normalizeTitle_blank_fallsBackToHost() {
        assertEquals("a.com", BookmarkRules.normalizeTitle("", "https://www.a.com/x"))
        assertEquals("a.com", BookmarkRules.normalizeTitle("   \t ", "https://a.com/x"))
    }

    @Test
    fun normalizeTitle_truncatesTo200() {
        val out = BookmarkRules.normalizeTitle("x".repeat(500), "https://a.com/")
        assertEquals(200, out.length)
    }

    @Test
    fun normalizeTitle_dropsControlCharacters() {
        assertEquals("ab", BookmarkRules.normalizeTitle("a\u0000\n\u0007b", "https://a.com/"))
    }

    @Test
    fun urlKey_ignoresSchemeHostCase_fragment_andLoneTrailingSlash() {
        assertEquals(BookmarkRules.urlKey("https://a.com"), BookmarkRules.urlKey("HTTPS://A.com/#x"))
        assertEquals(BookmarkRules.urlKey("https://a.com/"), BookmarkRules.urlKey("https://a.com"))
    }

    @Test
    fun urlKey_keepsTrailingSlashOfNonEmptyPath() {
        assertNotEquals(BookmarkRules.urlKey("https://a.com/p/"), BookmarkRules.urlKey("https://a.com/p"))
    }

    @Test
    fun urlKey_keepsQuery() {
        assertNotEquals(BookmarkRules.urlKey("https://a.com/?a=1"), BookmarkRules.urlKey("https://a.com/"))
    }

    @Test
    fun urlKey_keepsPathCase_andDistinguishesScheme() {
        assertNotEquals(BookmarkRules.urlKey("https://a.com/Abc"), BookmarkRules.urlKey("https://a.com/abc"))
        assertNotEquals(BookmarkRules.urlKey("http://a.com/"), BookmarkRules.urlKey("https://a.com/"))
    }
}
