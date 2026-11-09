package app.pixel.browser.core.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetscapeTagNameTest {
    @Test
    fun tagNameMustEndWithWhitespaceSlashOrGt() {
        val html =
            "<DL><DT><A HREF=\"https://a.com/\">x</A>" +
                "<DT><a-b HREF=\"https://evil.com/\">y</a-b><DT><A\tHREF=\"https://tab.com/\">t</A></DL>"
        val parsed = NetscapeBookmarks.parse(html.toByteArray()) as NetscapeParse.Parsed
        assertEquals(listOf("https://a.com/", "https://tab.com/"), parsed.root.bookmarks.map { it.url })
        assertTrue(parsed.skipped == 0)
    }
}
