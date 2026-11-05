package app.pixel.browser.core.omnibox

import app.pixel.browser.core.omnibox.OmniboxResult.Empty
import app.pixel.browser.core.omnibox.OmniboxResult.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniboxClassifierTest {
    private val google = SearchEngines.Google

    @Test
    fun ac2_emptyOrWhitespaceInput_isEmpty() {
        listOf("", "   ", "\t\n").forEach { input ->
            assertEquals("input='$input'", Empty, OmniboxClassifier.classify(input, google))
        }
    }

    @Test
    fun ac12_searchResult_carriesQueryAndTemplateUrl() {
        assertEquals(
            Search(query = "hello world", url = "https://www.google.com/search?q=hello%20world"),
            OmniboxClassifier.classify("hello world", google),
        )
    }

    @Test
    fun ac16_searchUsesGivenEngine() {
        assertEquals(
            Search("kotlin", "https://duckduckgo.com/?q=kotlin"),
            OmniboxClassifier.classify("kotlin", SearchEngines.DuckDuckGo),
        )
    }

    @Test
    fun ac20_httpWithoutHost_searchesTrimmedInputWithEngineUrl() {
        assertEquals(
            Search("https://", google.searchUrl("https://")),
            OmniboxClassifier.classify("  https://  ", google),
        )
    }

    @Test
    fun edge_veryLongUrlInput_doesNotThrow() {
        val input = "a".repeat(10_000) + ".com/" + "p".repeat(10_000)
        assertNotNull(OmniboxClassifier.classify(input, google))
    }

    @Test
    fun edge_veryLongQueryInput_doesNotThrowAndSearches() {
        val input = "word ".repeat(2_500).trim()
        assertTrue(OmniboxClassifier.classify(input, google) is Search)
    }

    @Test
    fun edge_javascriptWithTabInside_isNotNavigate() {
        val result = OmniboxClassifier.classify("java\tscript:alert(1)", google)
        assertTrue("was $result", result !is OmniboxResult.Navigate)
    }
}
