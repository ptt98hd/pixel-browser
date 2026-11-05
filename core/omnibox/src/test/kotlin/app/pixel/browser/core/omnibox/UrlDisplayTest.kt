package app.pixel.browser.core.omnibox

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlDisplayTest {
    @Test
    fun ac26_host_stripsWwwPrefixAndPathQuery() {
        assertEquals("example.com", UrlDisplay.host("https://www.example.com/path?q=1"))
    }

    @Test
    fun ac26_host_keepsPort() {
        assertEquals("example.com:8080", UrlDisplay.host("http://example.com:8080/a"))
    }

    @Test
    fun ac26_host_stripsOnlyExactWwwDotPrefix() {
        assertEquals("www2.example.com", UrlDisplay.host("https://www2.example.com"))
    }

    @Test
    fun ac26_host_keepsWwwWhenRemainderHasNoDot() {
        assertEquals("www.com", UrlDisplay.host("https://www.com"))
    }

    @Test
    fun ac26_host_keepsIpv6BracketsAndPort() {
        assertEquals("[::1]:8080", UrlDisplay.host("http://[::1]:8080/"))
    }

    @Test
    fun ac26_host_keepsPunycode() {
        assertEquals("xn--bcher-kva.de", UrlDisplay.host("https://xn--bcher-kva.de"))
    }

    @Test
    fun ac26_host_aboutBlankIsEmpty() {
        assertEquals("", UrlDisplay.host("about:blank"))
    }

    @Test
    fun ac26_host_emptyIsEmpty() {
        assertEquals("", UrlDisplay.host(""))
    }

    @Test
    fun ac26_host_unparseableReturnedAsIs() {
        assertEquals("not a url", UrlDisplay.host("not a url"))
    }

    @Test
    fun ac27_forEditing_returnsFullUrl() {
        assertEquals("https://example.com/a", UrlDisplay.forEditing("https://example.com/a"))
    }

    @Test
    fun ac27_forEditing_aboutBlankIsEmpty() {
        assertEquals("", UrlDisplay.forEditing("about:blank"))
    }
}
