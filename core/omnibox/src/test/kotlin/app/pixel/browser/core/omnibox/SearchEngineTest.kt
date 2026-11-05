package app.pixel.browser.core.omnibox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchEngineTest {
    @Test
    fun ac12_searchUrl_replacesPlaceholderAndEncodesSpaceAsPercent20() {
        assertEquals(
            "https://www.google.com/search?q=hello%20world",
            SearchEngines.Google.searchUrl("hello world"),
        )
    }

    @Test
    fun ac13_searchUrl_percentEncodesReservedCharacters() {
        assertEquals(
            "https://www.google.com/search?q=a%26b%3Dc%23d%2Fe%3Ff%2Bg%25",
            SearchEngines.Google.searchUrl("a&b=c#d/e?f+g%"),
        )
    }

    @Test
    fun ac13_searchUrl_percentEncodesUtf8BytesUppercaseHex() {
        assertEquals(
            "https://www.google.com/search?q=ph%E1%BB%9F%20b%C3%B2",
            SearchEngines.Google.searchUrl("phở bò"),
        )
    }

    @Test
    fun ac13_searchUrl_keepsRfc3986UnreservedCharacters() {
        val unreserved = "AZaz09-._~"
        assertEquals(
            "https://www.google.com/search?q=$unreserved",
            SearchEngines.Google.searchUrl(unreserved),
        )
    }

    @Test
    fun ac14_searchUrl_replacesEveryPlaceholder() {
        val engine = SearchEngine("custom", "Custom", "https://s.example/?q=%s&alt=%s")
        assertEquals("https://s.example/?q=x%20y&alt=x%20y", engine.searchUrl("x y"))
    }

    @Test
    fun ac15_defaultEngines_areGoogleDuckDuckGoBraveBingInOrder() {
        assertEquals(
            listOf(SearchEngines.Google, SearchEngines.DuckDuckGo, SearchEngines.Brave, SearchEngines.Bing),
            SearchEngines.all,
        )
        assertEquals(
            listOf(
                "https://www.google.com/search?q=%s",
                "https://duckduckgo.com/?q=%s",
                "https://search.brave.com/search?q=%s",
                "https://www.bing.com/search?q=%s",
            ),
            SearchEngines.all.map { it.template },
        )
    }

    @Test
    fun ac15_defaultEngine_isGoogle() {
        assertEquals(SearchEngines.Google, SearchEngines.Default)
    }

    @Test
    fun ac15_engineIds_areUnique() {
        val ids = SearchEngines.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun ac17_templateWithoutPlaceholder_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchEngine("bad", "Bad", "https://s.example/?q=")
        }
    }

    @Test
    fun ac17_templateWithJavascriptScheme_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchEngine("bad", "Bad", "javascript:%s")
        }
    }

    @Test
    fun ac17_templateWithNonHttpScheme_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchEngine("bad", "Bad", "ftp://s.example/?q=%s")
        }
    }

    @Test
    fun ac17_httpTemplate_isAccepted() {
        SearchEngine("ok", "Ok", "http://s.example/?q=%s")
    }

    @Test
    fun ac18_homeUrl_isSchemeAndHostWithTrailingSlash() {
        assertEquals("https://www.google.com/", SearchEngines.Google.homeUrl)
        assertEquals("https://duckduckgo.com/", SearchEngines.DuckDuckGo.homeUrl)
    }
}
